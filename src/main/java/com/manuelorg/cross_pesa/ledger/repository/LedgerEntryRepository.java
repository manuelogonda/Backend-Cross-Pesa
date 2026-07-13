package com.manuelorg.cross_pesa.ledger.repository;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Useful for showing a user their wallet statement
    List<LedgerEntry> findAllByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
