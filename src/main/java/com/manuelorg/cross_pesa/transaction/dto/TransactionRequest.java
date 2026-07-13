package com.manuelorg.cross_pesa.transaction.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionRequest {

        /**
         * Request DTO for external remittances.
         * Requires a Beneficiary ID instead of a Destination Wallet.
         */
        public record SendMoneyRequest(
                @NotNull(message = "Source wallet ID is required")
                UUID sourceWalletId,

                @NotNull(message = "Beneficiary ID is required")
                UUID beneficiaryId,

                @NotNull(message = "Source currency is required")
                Currency sourceCurrency,

                @NotNull(message = "Destination currency is required")
                Currency destinationCurrency,

                @NotNull(message = "Amount is required")
                @Positive(message = "Transfer amount must be strictly greater than zero")
                BigDecimal amount,

                @NotNull(message = "Idempotency key is required to prevent duplicate charges")
                UUID idempotencyKey
        ) {}

        /**
         * Request DTO for internal wallet-to-wallet exchanges.
         * Requires a Destination Wallet ID instead of a Beneficiary.
         */
        public record ExchangeFundsRequest(
                @NotNull(message = "Source wallet ID is required")
                UUID sourceWalletId,

                @NotNull(message = "Destination wallet ID is required")
                UUID destinationWalletId,

                @NotNull(message = "Source currency is required")
                Currency sourceCurrency,

                @NotNull(message = "Destination currency is required")
                Currency destinationCurrency,

                @NotNull(message = "Amount is required")
                @Positive(message = "Exchange amount must be strictly greater than zero")
                BigDecimal amount,

                @NotNull(message = "Idempotency key is required")
                UUID idempotencyKey
        ) {}
}