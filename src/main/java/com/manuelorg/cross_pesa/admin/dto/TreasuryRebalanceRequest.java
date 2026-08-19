package com.manuelorg.cross_pesa.admin.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TreasuryRebalanceRequest(
        @NotNull(message = "Source currency is required") Currency sourceCurrency,
        @NotNull(message = "Withdraw amount is required") @Positive(message = "Withdraw amount must be positive") BigDecimal withdrawAmount,
        @NotNull(message = "Target currency is required") Currency targetCurrency,
        @NotNull(message = "Deposit amount is required") @Positive(message = "Deposit amount must be positive") BigDecimal depositAmount,
        @NotBlank(message = "Notes are required for audit") String notes
) {}
