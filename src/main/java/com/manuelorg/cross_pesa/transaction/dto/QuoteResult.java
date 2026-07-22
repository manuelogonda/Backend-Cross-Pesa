package com.manuelorg.cross_pesa.transaction.dto;

import java.math.BigDecimal;

public record QuoteResult(
        BigDecimal amountSent,           // e.g., 10,000.00 GBP
        BigDecimal totalPlatformFee,     // e.g., 59.36 GBP
        BigDecimal platformMarkupFee,    // e.g., 29.36 GBP (Tier calculation)
        BigDecimal routingCostFee,       // e.g., 30.00 GBP (Corridor cost)
        BigDecimal amountAfterFees,      // e.g., 9,940.64 GBP (Ledger debit)
        BigDecimal payoutAmountTarget,   // e.g., 1,650,400.75 KES (Ledger credit)
        BigDecimal appliedExchangeRate   // e.g., 166.0256
) {}
