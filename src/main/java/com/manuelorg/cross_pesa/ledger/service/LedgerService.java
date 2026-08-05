package com.manuelorg.cross_pesa.ledger.service;

import com.manuelorg.cross_pesa.auth.entity.User;
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
     */
    @Transactional
    public void recordSimpleTransfer(Transaction transaction, Wallet sourceWallet, Wallet targetWallet, BigDecimal amount, EntryClass entryClass, String description) {
        if (sourceWallet.getCurrency() != targetWallet.getCurrency()) {
            throw new IllegalArgumentException("Direct simple transfers must be in the same currency.");
        }

        // 1. Lock and fetch fresh wallet states
        Wallet lockedSource = lockAndGetWallet(sourceWallet.getId());
        Wallet lockedTarget = lockAndGetWallet(targetWallet.getId());

        // 2. Enforce retail insufficient funds check
        if (lockedSource.getWalletType() == WalletType.USER_RETAIL && lockedSource.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(String.format("Insufficient funds in wallet %s. Available: %s, Attempted Debit: %s",
                    lockedSource.getId(), lockedSource.getBalance(), amount));
        }

        // 3. Calculate new balances
        BigDecimal sourceNewBalance = lockedSource.getBalance().subtract(amount);
        BigDecimal targetNewBalance = lockedTarget.getBalance().add(amount);

        // 4. Create DEBIT leg
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

        // 5. Create CREDIT leg
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

        // 6. Update entity cache/database balances
        lockedSource.setBalance(sourceNewBalance);
        lockedTarget.setBalance(targetNewBalance);
        walletRepository.saveAll(List.of(lockedSource, lockedTarget));

        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * WRITE: Records a single-leg deposit from an external gateway.
     */
    @Transactional
    public void recordGatewayDeposit(Transaction transaction, Wallet targetWallet, BigDecimal amount, String description) {
        Wallet lockedTarget = lockAndGetWallet(targetWallet.getId());

        BigDecimal newBalance = lockedTarget.getBalance().add(amount);

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

        lockedTarget.setBalance(newBalance);
        walletRepository.save(lockedTarget);
        ledgerEntryRepository.save(depositEntry);

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Helper to fetch a wallet with a pessimistic write lock to prevent race conditions.
     */
    private Wallet lockAndGetWallet(UUID walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found with ID: " + walletId));
    }
}
