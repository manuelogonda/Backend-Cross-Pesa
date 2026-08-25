package com.manuelorg.cross_pesa.beneficiaries.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryResponse;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.payment.flutterwave.FlutterwaveTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final FlutterwaveTransferService flutterwaveTransferService;

    @Transactional(readOnly = true)
    public Page<BeneficiaryResponse> getUserBeneficiaries(UUID userId, Pageable pageable) {
        return beneficiaryRepository.findAllByUserId(userId, pageable)
                .map(BeneficiaryResponse::fromEntity);
    }

    @Transactional
    public BeneficiaryResponse createBeneficiary(User currentUser, BeneficiaryRequest request) {
        // 1. Check for Duplicate Routing (A user shouldn't add the exact same bank account twice)
        if (beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), request.payoutProvider(), request.accountNumber().trim())) {
            throw new IllegalArgumentException("You have already saved a beneficiary with this exact account number and provider.");
        }

        // 2. Build and Save
        Beneficiary beneficiary = Beneficiary.builder()
                .user(currentUser)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .beneficiaryType(request.beneficiaryType())
                .email(request.email().trim())
                .phoneNumber(request.phoneNumber().trim())
                .countryCode(request.countryCode().trim().toUpperCase())
                .city(request.city() != null ? request.city().trim() : null)
                .payoutMethod(request.payoutMethod())
                .payoutProvider(request.payoutProvider())
                .accountNumber(request.accountNumber().trim())
                .bankCode(request.bankCode().trim())
                .accountCurrency(request.accountCurrency())
                .build();

        Beneficiary savedBeneficiary = beneficiaryRepository.save(beneficiary);
        return BeneficiaryResponse.fromEntity(savedBeneficiary);
    }

    @Transactional
    public void deleteBeneficiary(User currentUser, UUID beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found or unauthorized"));

        beneficiaryRepository.delete(beneficiary);
    }

    @Transactional
    public BeneficiaryResponse updateBeneficiary(User currentUser, UUID id, BeneficiaryRequest request) {
        // 1. Fetch existing beneficiary and ensure it belongs to the logged-in user
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found or unauthorized"));

        // 2. Check routing duplicate if provider or account number changed
        boolean routingChanged = !beneficiary.getPayoutProvider().equals(request.payoutProvider())
                || !beneficiary.getAccountNumber().equalsIgnoreCase(request.accountNumber().trim())
                || !request.bankCode().trim().equalsIgnoreCase(beneficiary.getBankCode());
        if (routingChanged && beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), request.payoutProvider(), request.accountNumber().trim())) {
            throw new IllegalArgumentException("You have already saved a beneficiary with this exact account number and provider.");
        }

        // 3. Update the fields
        beneficiary.setFirstName(request.firstName().trim());
        beneficiary.setLastName(request.lastName().trim());
        beneficiary.setBeneficiaryType(request.beneficiaryType());
        beneficiary.setEmail(request.email().trim());
        beneficiary.setPhoneNumber(request.phoneNumber().trim());
        beneficiary.setCountryCode(request.countryCode().trim().toUpperCase());
        beneficiary.setCity(request.city() != null ? request.city().trim() : null);
        beneficiary.setPayoutMethod(request.payoutMethod());
        beneficiary.setPayoutProvider(request.payoutProvider());
        beneficiary.setAccountNumber(request.accountNumber().trim());
        beneficiary.setBankCode(request.bankCode().trim());
        beneficiary.setAccountCurrency(request.accountCurrency());

        // Routing details changed — the previously cached recipient descriptor
        // points at the old destination, so it must never be reused.
        if (routingChanged) {
            beneficiary.setGatewayRecipientCode(null);
            flutterwaveTransferService.invalidateRecipient(beneficiary.getId());
        }

        // 4. Save and return the mapped response
        Beneficiary updatedBeneficiary = beneficiaryRepository.save(beneficiary);
        return BeneficiaryResponse.fromEntity(updatedBeneficiary);
    }
}