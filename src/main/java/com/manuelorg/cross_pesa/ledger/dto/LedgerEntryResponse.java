package com.manuelorg.cross_pesa.ledger.dto;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID walletId,
        String entryClass,
        BigDecimal debit,
        BigDecimal credit,
        String currency,
        BigDecimal balanceAfter,
        String description,
        OffsetDateTime createdAt
) {
    /**
     * Maps a LedgerEntry entity into an immutable DTO.
     */
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction() != null ? entry.getTransaction().getId() : null,
                entry.getWallet() != null ? entry.getWallet().getId() : null,
                entry.getEntryClass(),
                entry.getDebit(),
                entry.getCredit(),
                entry.getCurrency() != null ? entry.getCurrency().name() : null,
                entry.getBalanceAfter(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
