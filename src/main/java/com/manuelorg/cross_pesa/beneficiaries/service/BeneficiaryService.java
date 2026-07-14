package com.manuelorg.cross_pesa.beneficiaries.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryResponse;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;

    @Transactional(readOnly = true)
    public Page<BeneficiaryResponse> getUserBeneficiaries(UUID userId, Pageable pageable) {
        return beneficiaryRepository.findAllByUserId(userId, pageable)
                .map(BeneficiaryResponse::fromEntity);
    }


    @Transactional
    public BeneficiaryResponse createBeneficiary(User currentUser, BeneficiaryRequest request) {
        // 1. Check for Duplicate Routing (A user shouldn't add the exact same bank account twice)
        if (beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), request.payoutProvider(), request.accountNumber())) {
            throw new IllegalArgumentException("You have already saved a beneficiary with this exact account number and provider.");
        }

        // 2. Check Global Unique Constraints
        if (beneficiaryRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("A beneficiary with this email is already registered in the system.");
        }
        if (beneficiaryRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new IllegalArgumentException("A beneficiary with this phone number is already registered.");
        }

        // 3. Build and Save
        Beneficiary beneficiary = Beneficiary.builder()
                .user(currentUser)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .beneficiaryType(request.beneficiaryType())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .countryCode(request.countryCode().toUpperCase())
                .city(request.city())
                .payoutMethod(request.payoutMethod())
                .payoutProvider(request.payoutProvider())
                .accountNumber(request.accountNumber())
                .accountCurrency(request.accountCurrency())
                .build();

        Beneficiary savedBeneficiary = beneficiaryRepository.save(beneficiary);
        return BeneficiaryResponse.fromEntity(savedBeneficiary);
    }

    @Transactional
    public void deleteBeneficiary(User currentUser, UUID beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found."));

        // Security Check: Only the owner can delete their beneficiary
        if (!beneficiary.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to delete this beneficiary.");
        }

        beneficiaryRepository.delete(beneficiary);
    }

    @Transactional
    public BeneficiaryResponse updateBeneficiary(User currentUser, UUID id, BeneficiaryRequest request) {
        // 1. Fetch existing beneficiary and ensure it belongs to the logged-in user
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Beneficiary not found or unauthorized"));

        if (!beneficiary.getEmail().equals(request.email()) &&
                beneficiaryRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email is already in use by another beneficiary");
        }

        // 2. Update the fields
        beneficiary.setFirstName(request.firstName());
        beneficiary.setLastName(request.lastName());
        beneficiary.setBeneficiaryType(request.beneficiaryType());
        beneficiary.setEmail(request.email());
        beneficiary.setPhoneNumber(request.phoneNumber());
        beneficiary.setCountryCode(request.countryCode());
        beneficiary.setCity(request.city());
        beneficiary.setPayoutMethod(request.payoutMethod());
        beneficiary.setPayoutProvider(request.payoutProvider());
        beneficiary.setAccountNumber(request.accountNumber());
        beneficiary.setAccountCurrency(request.accountCurrency());

        // 3. Save and return the mapped response
        Beneficiary updatedBeneficiary = beneficiaryRepository.save(beneficiary);
        return BeneficiaryResponse.fromEntity(updatedBeneficiary);
    }
}