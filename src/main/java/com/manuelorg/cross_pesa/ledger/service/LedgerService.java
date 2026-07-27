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

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final EntityManager entityManager;

    /**
     * READ: Fetches the paginated statement for the user's primary retail wallet.
     * Guaranteed to only fetch records belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getWalletStatement(User currentUser, Pageable pageable) {
        // 1. Fetch the user's single retail wallet using the strict discriminator
        Wallet wallet = walletRepository.findByUserIdAndWalletType(currentUser.getId(), WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for this user."));

        // 2. Fetch and map the paginated ledger entries
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(LedgerEntryResponse::fromEntity);
    }

    /**
     * WRITE: Records a strict double-entry transfer for simple same-currency movements.
     * (Note: Complex multi-leg FX transfers are handled directly in TransactionService).
     */
    @Transactional
    public void recordSimpleTransfer(Transaction transaction, Wallet sourceWallet, Wallet targetWallet, BigDecimal amount, EntryClass entryClass, String description) {
        // 1. Sanity check: Ensure currencies match (Cross-currency requires an FX exchange intermediate step)
        if (sourceWallet.getCurrency() != targetWallet.getCurrency()) {
            throw new IllegalArgumentException("Direct simple transfers must be in the same currency.");
        }

        // 2. Create the DEBIT leg (Money leaving the source)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(sourceWallet)
                .entryClass(entryClass) // Type-safe Enum mapping
                .debit(amount)
                .currency(sourceWallet.getCurrency())
                .description(description + " (Outgoing)")
                .build();

        // 3. Create the CREDIT leg (Money entering the target)
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(targetWallet)
                .entryClass(entryClass) // Type-safe Enum mapping
                .credit(amount)
                .currency(targetWallet.getCurrency())
                .description(description + " (Incoming)")
                .build();

        // 4. Save both to the ledger.
        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));

        // 5. CRITICAL: Clear Hibernate cache so the PostgreSQL trigger's balance math is fetched next time
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * WRITE: Records a single-leg deposit from an external gateway (like Flutterwave).
     * Since the actual cash is held at the gateway, we only record the liability (credit) on our platform.
     */
    @Transactional
    public void recordGatewayDeposit(Transaction transaction, Wallet targetWallet, BigDecimal amount, String description) {
        LedgerEntry depositEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(targetWallet)
                .entryClass(EntryClass.DEPOSIT) // Type-safe Enum mapping
                .credit(amount)
                .currency(targetWallet.getCurrency())
                .description(description)
                .build();

        ledgerEntryRepository.save(depositEntry);

        // CRITICAL: Clear Hibernate cache
        entityManager.flush();
        entityManager.clear();
    }
}
