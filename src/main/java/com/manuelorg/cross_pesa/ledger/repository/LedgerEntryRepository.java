package com.manuelorg.cross_pesa.ledger.repository;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Fetches all ledger entries belonging to a specific wallet (User statement).
     */
    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    /**
     * Fetches the most recent ledger entry for a wallet — used to derive the current balance
     * without relying on the wallet balance cache.
     */
    Optional<LedgerEntry> findTopByWalletIdOrderByEntrySeqDesc(UUID walletId);

    /**
     * Fetches all entries associated with a single parent Transaction (Audit view).
     */
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Paginated lookup for compliance officers by entry class (e.g., all REFUND or FX_CLEARING entries).
     */
    Page<LedgerEntry> findByEntryClass(EntryClass entryClass, Pageable pageable);

    /**
     * Idempotency guard for reversals: has a refund already been posted for this transaction?
     */
    boolean existsByTransactionIdAndEntryClass(UUID transactionId, EntryClass entryClass);

    /**
     * Audit Query: Fetches ledger movements for a wallet within a custom date range.
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.wallet.id = :walletId AND l.createdAt BETWEEN :startDate AND :endDate ORDER BY l.createdAt DESC")
    Page<LedgerEntry> findWalletStatementBetween(
            @Param("walletId") UUID walletId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable
    );
}
