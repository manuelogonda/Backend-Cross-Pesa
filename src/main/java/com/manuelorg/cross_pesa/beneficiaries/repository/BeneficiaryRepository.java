package com.manuelorg.cross_pesa.beneficiaries.repository;

import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    // Fetch all contacts for the dashboard
    Page<Beneficiary> findAllByUserId(UUID userId, Pageable pageable);

    Optional<Beneficiary> findByIdAndUserId(UUID id, UUID userId);

    // Enforces the uk_user_beneficiary_routing constraint
    boolean existsByUserIdAndPayoutProviderAndAccountNumber(
            UUID userId,
            PayoutProvider payoutProvider,
            String accountNumber
    );

    /**
     * Persists the cached gateway transfer recipient code. Must be called
     * within an active transaction.
     */
    @Modifying
    @Query("update Beneficiary b set b.gatewayRecipientCode = :code where b.id = :id")
    int updateGatewayRecipientCode(@Param("id") UUID id, @Param("code") String code);
}
