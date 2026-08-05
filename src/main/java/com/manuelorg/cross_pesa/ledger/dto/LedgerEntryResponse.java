package com.manuelorg.cross_pesa.ledger.dto;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID walletId,
        EntryClass entryClass,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount,        // Net impact (credit - debit)
        BigDecimal balanceAfter,
        String currency,
        String description,
        OffsetDateTime createdAt
) {
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        BigDecimal netImpact = entry.getCredit() != null && entry.getDebit() != null
                ? entry.getCredit().subtract(entry.getDebit())
                : BigDecimal.ZERO;

        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction() != null ? entry.getTransaction().getId() : null,
                entry.getWallet() != null ? entry.getWallet().getId() : null,
                entry.getEntryClass(),
                entry.getDebit() != null ? entry.getDebit() : BigDecimal.ZERO,
                entry.getCredit() != null ? entry.getCredit() : BigDecimal.ZERO,
                netImpact,
                entry.getBalanceAfter() != null ? entry.getBalanceAfter() : BigDecimal.ZERO,
                entry.getCurrency() != null ? entry.getCurrency().name() : "USD",
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}