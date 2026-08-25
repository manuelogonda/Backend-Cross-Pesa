package com.manuelorg.cross_pesa.beneficiaries.dto;

import com.manuelorg.cross_pesa.beneficiaries.entity.BeneficiaryType;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutMethod;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BeneficiaryRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 50)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50)
        String lastName,

        @NotNull(message = "Beneficiary type is required")
        BeneficiaryType beneficiaryType,

        @NotBlank(message = "Email is required")
        @Email(message = "Provide a valid email address")
        @Size(max = 100)
        String email,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        String phoneNumber,

        @NotBlank(message = "Country code is required")
        @Size(min = 2, max = 2, message = "Country code must be a 2-letter ISO code")
        String countryCode,

        @Size(max = 50)
        String city,

        @NotNull(message = "Payout method is required")
        PayoutMethod payoutMethod,

        @NotNull(message = "Payout provider is required")
        PayoutProvider payoutProvider,

        @NotBlank(message = "Account number or reference routing detail is required")
        @Size(max = 50)
        String accountNumber,

        @NotBlank(message = "Bank or network code is required for payouts")
        @Size(max = 20)
        String bankCode,

        @NotNull(message = "Account currency is required")
        Currency accountCurrency
) {}
