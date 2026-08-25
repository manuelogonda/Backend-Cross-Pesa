package com.manuelorg.cross_pesa.transaction.dto;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class TransactionResponse {

    /**
     * Response DTO for external remittances (Paystack beneficiary payouts).
     */
    public record SendMoneyResponse(
            UUID id,
            UUID senderId,
            UUID sourceWalletId,
            UUID beneficiaryId,
            String sourceCurrency,
            String destinationCurrency,
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal markupFee,
            BigDecimal routingFee,
            BigDecimal totalFee,
            BigDecimal amountReceived,
            BigDecimal fxRateApplied,
            BigDecimal usdNormalizationRate,
            String reference,
            String payoutGateway,
            String payoutReference,
            String status,
            OffsetDateTime createdAt
    ) {
        public static SendMoneyResponse fromEntity(Transaction tx) {
            String resolvedRef = tx.getGatewayReference() != null ? tx.getGatewayReference()
                    : (tx.getPayoutReference() != null ? tx.getPayoutReference() : tx.getId().toString());

            return new SendMoneyResponse(
                    tx.getId(),
                    tx.getSender().getId(),
                    tx.getSourceWallet().getId(),
                    tx.getBeneficiary() != null ? tx.getBeneficiary().getId() : null,
                    tx.getSourceCurrency().name(),
                    tx.getDestinationCurrency().name(),
                    tx.getGrossAmount(),
                    tx.getNetAmount(),
                    tx.getMarkupFee(),
                    tx.getRoutingFee(),
                    tx.getTotalFee(),
                    tx.getDestinationAmount(),
                    tx.getFxRateApplied(),
                    tx.getUsdNormalizationRate(),
                    resolvedRef,
                    tx.getPayoutGateway(),
                    tx.getPayoutReference(),
                    tx.getStatus().name(),
                    tx.getCreatedAt()
            );
        }
    }
}
