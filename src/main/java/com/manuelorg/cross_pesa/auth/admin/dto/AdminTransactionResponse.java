package com.manuelorg.cross_pesa.auth.admin.dto;

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
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal destinationAmount,
        String destinationCurrency,
        BigDecimal exchangeRate,
        BigDecimal platformFee,
        TransactionStatus status,
        String gatewayReference,
        OffsetDateTime createdAt
) {
    public static AdminTransactionResponse fromEntity(Transaction tx) {
        String benName = tx.getBeneficiary() != null ?
                tx.getBeneficiary().getFirstName() + " " + tx.getBeneficiary().getLastName() : "Internal Exchange";

        String benAccount = tx.getBeneficiary() != null ?
                tx.getBeneficiary().getAccountNumber() : "N/A";

        return new AdminTransactionResponse(
                tx.getId(),
                tx.getSender().getFirstName() + " " + tx.getSender().getLastName(),
                tx.getSender().getEmail(),
                benName,
                benAccount,
                tx.getSourceAmount(),
                tx.getSourceCurrency().name(),
                tx.getDestinationAmount(),
                tx.getDestinationCurrency().name(),
                tx.getFxRateApplied(),
                tx.getTransferFee(),
                tx.getStatus(),
                tx.getGatewayReference(),
                tx.getCreatedAt()
        );
    }
}
