package com.manuelorg.cross_pesa.admin.dto;

import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTransactionResponse(
        UUID transactionId,
        String senderName,
        String senderEmail,
        String beneficiaryName,
        String beneficiaryAccount,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        String sourceCurrency,
        BigDecimal destinationAmount,
        String destinationCurrency,
        BigDecimal exchangeRate,
        BigDecimal usdNormalizationRate,
        BigDecimal markupFee,
        BigDecimal routingFee,
        BigDecimal totalFee,
        TransactionStatus status,
        String gatewayReference,
        OffsetDateTime createdAt
) {
    public static AdminTransactionResponse fromEntity(Transaction tx) {
        // Handle optional beneficiary safely (P2P transfers don't have one)
        String benName = tx.getBeneficiary() != null ?
                tx.getBeneficiary().getFirstName() + " " + tx.getBeneficiary().getLastName() :
                (tx.getDestinationWallet() != null ? "P2P Transfer" : "System Exchange");

        String benAccount = tx.getBeneficiary() != null ?
                tx.getBeneficiary().getAccountNumber() :
                (tx.getDestinationWallet() != null ? tx.getDestinationWallet().getId().toString() : "N/A");

        return new AdminTransactionResponse(
                tx.getId(),
                tx.getSender().getFirstName() + " " + tx.getSender().getLastName(),
                tx.getSender().getEmail(),
                benName,
                benAccount,
                tx.getGrossAmount(),   // Replaced sourceAmount
                tx.getNetAmount(),     // Added for transparency
                tx.getSourceCurrency().name(),
                tx.getDestinationAmount(),
                tx.getDestinationCurrency().name(),
                tx.getFxRateApplied(),
                tx.getUsdNormalizationRate(), // Admin audit trail
                tx.getMarkupFee(),     // Replaced transferFee
                tx.getRoutingFee(),    // Replaced transferFee
                tx.getTotalFee(),      // Replaced transferFee
                tx.getStatus(),
                tx.getGatewayReference() != null ? tx.getGatewayReference() : tx.getPayoutReference(),
                tx.getCreatedAt()
        );
    }
}
