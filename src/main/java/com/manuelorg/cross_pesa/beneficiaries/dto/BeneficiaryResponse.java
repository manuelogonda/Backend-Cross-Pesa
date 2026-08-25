package com.manuelorg.cross_pesa.beneficiaries.dto;

import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;

import java.util.UUID;

public record BeneficiaryResponse(
        UUID id,
        String firstName,
        String lastName,
        String beneficiaryType,
        String email,
        String phoneNumber,
        String countryCode,
        String city,
        String payoutMethod,
        String payoutProvider,
        String accountNumber,
        String bankCode,
        String accountCurrency
) {
    public static BeneficiaryResponse fromEntity(Beneficiary b) {
        return new BeneficiaryResponse(
                b.getId(),
                b.getFirstName(),
                b.getLastName(),
                b.getBeneficiaryType().name(),
                b.getEmail(),
                b.getPhoneNumber(),
                b.getCountryCode(),
                b.getCity(),
                b.getPayoutMethod().name(),
                b.getPayoutProvider().getDbValue(), // Delivers clean display value like "EQUITY BANK"
                b.getAccountNumber(),
                b.getBankCode(),
                b.getAccountCurrency().name()
        );
    }
}
