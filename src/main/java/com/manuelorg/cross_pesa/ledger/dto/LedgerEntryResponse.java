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
        BigDecimal netImpact = entry.getCredit().subtract(entry.getDebit());

        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getWallet().getId(),
                entry.getEntryClass(),
                entry.getDebit(),
                entry.getCredit(),
                netImpact,
                entry.getBalanceAfter(),
                entry.getCurrency().name(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
