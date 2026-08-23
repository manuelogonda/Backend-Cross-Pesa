package com.manuelorg.cross_pesa.ledger.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.exception.InsufficientFundsException;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final EntityManager entityManager;

    /**
     * READ: Fetches the paginated statement for the user's primary retail wallet.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getWalletStatement(User currentUser, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserIdAndWalletType(currentUser.getId(), WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for this user."));

        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(LedgerEntryResponse::fromEntity);
    }

    /**
     * WRITE: Records a strict double-entry transfer for simple same-currency movements.
     * Wallets are locked in deterministic UUID order to prevent deadlocks.
     */
    @Transactional
    public void recordSimpleTransfer(Transaction transaction, Wallet sourceWallet, Wallet targetWallet, BigDecimal amount, EntryClass entryClass, String description) {
        if (sourceWallet.getCurrency() != targetWallet.getCurrency()) {
            throw new IllegalArgumentException("Direct simple transfers must be in the same currency.");
        }

        // 1. Lock wallets in deterministic order (smaller UUID first) to prevent deadlocks
        Wallet lockedSource;
        Wallet lockedTarget;
        if (sourceWallet.getId().compareTo(targetWallet.getId()) < 0) {
            lockedSource = lockAndGetWallet(sourceWallet.getId());
            lockedTarget = lockAndGetWallet(targetWallet.getId());
        } else {
            lockedTarget = lockAndGetWallet(targetWallet.getId());
            lockedSource = lockAndGetWallet(sourceWallet.getId());
        }

        // 2. Derive current balances from the ledger (source of truth); fall back to wallet cache
        BigDecimal sourceCurrentBalance = getCurrentBalance(lockedSource);
        BigDecimal targetCurrentBalance = getCurrentBalance(lockedTarget);

        // 3. Enforce retail insufficient funds check
        if (lockedSource.getWalletType() == WalletType.USER_RETAIL && sourceCurrentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds in wallet %s. Available: %s, Attempted Debit: %s",
                    lockedSource.getId(), sourceCurrentBalance, amount));
        }

        // 4. Calculate new balances
        BigDecimal sourceNewBalance = sourceCurrentBalance.subtract(amount);
        BigDecimal targetNewBalance = targetCurrentBalance.add(amount);

        // 5. Create DEBIT leg
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(lockedSource)
                .entryClass(entryClass)
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .currency(lockedSource.getCurrency())
                .balanceAfter(sourceNewBalance)
                .description(description + " (Outgoing)")
                .build();

        // 6. Create CREDIT leg
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(lockedTarget)
                .entryClass(entryClass)
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .currency(lockedTarget.getCurrency())
                .balanceAfter(targetNewBalance)
                .description(description + " (Incoming)")
                .build();

        // 7. Persist ledger entries first (source of truth), then update wallet balance projection
        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));

        lockedSource.setBalance(sourceNewBalance);
        lockedTarget.setBalance(targetNewBalance);
        walletRepository.saveAll(List.of(lockedSource, lockedTarget));

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * WRITE: Records a single-leg credit deposit from an external gateway.
     */
    @Transactional
    public void recordGatewayDeposit(Transaction transaction, Wallet targetWallet, BigDecimal amount, String description) {
        Wallet lockedTarget = lockAndGetWallet(targetWallet.getId());

        // Derive current balance from the ledger; fall back to wallet cache if no entries exist yet
        BigDecimal currentBalance = getCurrentBalance(lockedTarget);
        BigDecimal newBalance = currentBalance.add(amount);

        LedgerEntry depositEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(lockedTarget)
                .entryClass(EntryClass.DEPOSIT)
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .currency(lockedTarget.getCurrency())
                .balanceAfter(newBalance)
                .description(description)
                .build();

        // Persist ledger entry first (source of truth), then update wallet balance projection
        ledgerEntryRepository.save(depositEntry);

        lockedTarget.setBalance(newBalance);
        walletRepository.save(lockedTarget);

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Derives the current balance for a wallet from the most recent ledger entry.
     * Falls back to the wallet's cached balance field if no ledger entries exist yet.
     */
    private BigDecimal getCurrentBalance(Wallet wallet) {
        return ledgerEntryRepository
                .findTopByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId())
                .map(LedgerEntry::getBalanceAfter)
                .orElse(wallet.getBalance());
    }

    /**
     * Helper to fetch a wallet with a pessimistic write lock to prevent race conditions.
     */
    private Wallet lockAndGetWallet(UUID walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found with ID: " + walletId));
    }
}
