package com.manuelorg.cross_pesa.transaction.dto;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionRequest {

        /**
         * Request DTO for external remittances.
         * Deducts funds + tiered fees from sender's wallet and sends net amount to external beneficiary.
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
         * REMOVED: ExchangeFundsRequest (P2P) was deprecated along with P2P transfers.
         * Outbound flows use {@link SendMoneyRequest} against a saved beneficiary.
         */
}