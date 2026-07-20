package com.manuelorg.cross_pesa.kycSubmission.repository;

import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycSubmissionRepository extends JpaRepository<KycSubmission, UUID> {

    // For the webhook to find the record
    Optional<KycSubmission> findBySmileJobId(String smileJobId);

    // For the user's personal dashboard (usually just a short list)
    List<KycSubmission> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // For the Admin Dashboard: Filter by status with pagination
    Page<KycSubmission> findByStatus(String status, Pageable pageable);

    // For the Admin Dashboard: Fetch all with pagination
    Page<KycSubmission> findAll(Pageable pageable);
}
