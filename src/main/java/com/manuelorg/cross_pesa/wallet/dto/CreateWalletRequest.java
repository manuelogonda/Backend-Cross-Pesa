package com.manuelorg.cross_pesa.wallet.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotNull(message = "Currency is required")
        Currency currency
) { }
