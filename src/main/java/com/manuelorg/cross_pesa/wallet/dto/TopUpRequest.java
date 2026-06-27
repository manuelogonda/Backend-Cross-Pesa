package com.manuelorg.cross_pesa.wallet.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull(message = "Currency is required")
        Currency currency,

        @NotNull(message = "Amount is required")
        @Positive(message = "Top-up amount must be greater than zero")
        BigDecimal amount
) {}
