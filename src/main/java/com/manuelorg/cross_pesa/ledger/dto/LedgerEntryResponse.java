package com.manuelorg.cross_pesa.ledger.dto;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        String entryType,
        String currency,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        OffsetDateTime createdAt
) {
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getEntryType().name(),
                entry.getCurrency().name(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
