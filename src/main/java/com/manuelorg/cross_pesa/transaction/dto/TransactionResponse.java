package com.manuelorg.cross_pesa.transaction.dto;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


public class TransactionResponse {

    /**
     * Response for external remittances.
     * Guaranteed to have a Beneficiary, but no destination wallet.
     */
    public record SendMoneyResponse(
            UUID id,
            UUID senderId,
            UUID sourceWalletId,
            UUID beneficiaryId, // Required for sends
            String sourceCurrency,
            String destinationCurrency,
            BigDecimal amountSent,
            BigDecimal amountReceived,
            BigDecimal transferFee,
            BigDecimal fxRateApplied,
            String reference,
            String status,
            OffsetDateTime createdAt
    ) {
        public static SendMoneyResponse fromEntity(Transaction tx) {
            return new SendMoneyResponse(
                    tx.getId(),
                    tx.getSender().getId(),
                    tx.getSourceWallet().getId(),
                    tx.getBeneficiary().getId(),
                    tx.getSourceCurrency().name(),
                    tx.getDestinationCurrency().name(),
                    tx.getSourceAmount(),
                    tx.getDestinationAmount(),
                    tx.getTransferFee(),
                    tx.getFxRateApplied(),
                    tx.getGatewayReference(),
                    tx.getStatus().name(),
                    tx.getCreatedAt()
            );
        }
    }

    /**
     * Response for internal wallet-to-wallet exchanges.
     * Guaranteed to have a Destination Wallet, but no beneficiary and no flat fee.
     */
    public record ExchangeResponse(
            UUID id,
            UUID senderId,
            UUID sourceWalletId,
            UUID destinationWalletId, // Required for exchanges
            String sourceCurrency,
            String destinationCurrency,
            BigDecimal amountExchanged,
            BigDecimal amountReceived,
            BigDecimal fxRateApplied,
            String reference,
            String status,
            OffsetDateTime createdAt
    ) {
        public static ExchangeResponse fromEntity(Transaction tx) {
            return new ExchangeResponse(
                    tx.getId(),
                    tx.getSender().getId(),
                    tx.getSourceWallet().getId(),
                    tx.getDestinationWallet().getId(),
                    tx.getSourceCurrency().name(),
                    tx.getDestinationCurrency().name(),
                    tx.getSourceAmount(),
                    tx.getDestinationAmount(),
                    tx.getFxRateApplied(),
                    tx.getGatewayReference(),
                    tx.getStatus().name(),
                    tx.getCreatedAt()
            );
        }
    }
}
