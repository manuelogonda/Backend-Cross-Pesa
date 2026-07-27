package com.manuelorg.cross_pesa.admin.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TreasuryRebalanceRequest(
        @NotNull Currency sourceCurrency,
        @NotNull @Positive BigDecimal withdrawAmount,
        @NotNull Currency targetCurrency,
        @NotNull @Positive BigDecimal depositAmount,
        @NotNull String notes
) {}
