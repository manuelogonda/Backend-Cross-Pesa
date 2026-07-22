package com.manuelorg.cross_pesa.ledger.repository;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * User Statement Query: Fetches paginated ledger entries for a specific wallet,
     * ordered newest first.
     */
    Page<LedgerEntry> findAllByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    /**
     * Date Range Statement Query: Fetches ledger entries for custom statement generation
     * (e.g., Monthly bank statements).
     */
    Page<LedgerEntry> findAllByWalletIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID walletId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Pageable pageable
    );

    /**
     * Audit & Debugging Query: Fetches all multi-leg entries tied to a single transaction ID.
     * In our double-entry architecture, this returns between 2 and 4+ rows
     * (User Debit, Markup Credit, Routing Credit, and FX Clearing).
     */
    List<LedgerEntry> findAllByTransactionId(UUID transactionId);
}
