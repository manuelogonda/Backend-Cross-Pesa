package com.manuelorg.cross_pesa.beneficiaries.repository;

import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    // Fetch all contacts for the dashboard
    List<Beneficiary> findAllByUserId(UUID userId);

    Optional<Beneficiary> findByIdAndUserId(UUID id, UUID userId);

    // Enforces the uk_user_beneficiary_routing constraint
    boolean existsByUserIdAndPayoutProviderAndAccountNumber(
            UUID userId,
            PayoutProvider payoutProvider,
            String accountNumber
    );

    // Enforces the global unique constraints on email and phone
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
