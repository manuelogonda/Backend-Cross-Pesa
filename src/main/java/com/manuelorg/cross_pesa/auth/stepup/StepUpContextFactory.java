package com.manuelorg.cross_pesa.auth.stepup;

import com.manuelorg.cross_pesa.admin.dto.TreasuryRebalanceRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class StepUpContextFactory {

    private StepUpContextFactory() {
    }

    public static String forTransactionSend(TransactionRequest.SendMoneyRequest request) {
        return join(
                "sourceWalletId", request.sourceWalletId(),
                "beneficiaryId", request.beneficiaryId(),
                "sourceCurrency", request.sourceCurrency(),
                "destinationCurrency", request.destinationCurrency(),
                "amount", request.amount(),
                "idempotencyKey", request.idempotencyKey()
        );
    }

    public static String forBeneficiaryCreate(BeneficiaryRequest request) {
        return join(
                "firstName", request.firstName(),
                "lastName", request.lastName(),
                "beneficiaryType", request.beneficiaryType(),
                "email", request.email(),
                "phoneNumber", request.phoneNumber(),
                "countryCode", request.countryCode(),
                "city", request.city(),
                "payoutMethod", request.payoutMethod(),
                "payoutProvider", request.payoutProvider(),
                "accountNumber", request.accountNumber(),
                "bankCode", request.bankCode(),
                "accountCurrency", request.accountCurrency()
        );
    }

    public static String forBeneficiaryUpdate(UUID beneficiaryId, BeneficiaryRequest request) {
        return "beneficiaryId=" + beneficiaryId + ";" + forBeneficiaryCreate(request);
    }

    public static String forBeneficiaryDelete(UUID beneficiaryId) {
        return "beneficiaryId=" + beneficiaryId;
    }

    public static String forTreasuryRebalance(TreasuryRebalanceRequest request) {
        return join(
                "sourceCurrency", request.sourceCurrency(),
                "withdrawAmount", request.withdrawAmount(),
                "targetCurrency", request.targetCurrency(),
                "depositAmount", request.depositAmount(),
                "notes", request.notes()
        );
    }

    private static String join(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i += 2) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(parts[i]).append('=').append(normalize(parts[i + 1]));
        }
        return builder.toString();
    }

    private static String normalize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }
        return Objects.toString(value);
    }
}
