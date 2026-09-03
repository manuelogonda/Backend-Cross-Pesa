package com.manuelorg.cross_pesa.kycSubmission.service;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycResponse;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycSubmissionRequest;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycService {

    private final KycSubmissionRepository kycRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<KycResponse> getUserSubmissions(UUID userId) {
        return kycRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(KycResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // --- ADMIN METHODS ---

    @Transactional(readOnly = true)
    public Page<KycResponse> getAllSubmissions(String status, Pageable pageable) {
        Page<KycSubmission> page = (status == null || status.isBlank())
                ? kycRepository.findAll(pageable)
                : kycRepository.findByStatus(status.toUpperCase(), pageable);

        return page.map(KycResponse::fromEntity);
    }

    @Transactional
    public KycResponse reviewSubmission(UUID submissionId, String action, String reason, User adminUser) {
        KycSubmission submission = kycRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        if (!submission.getStatus().equals("PENDING")) {
            throw new IllegalStateException("Submission has already been " + submission.getStatus());
        }

        submission.setReviewedBy(adminUser);
        submission.setReviewedAt(LocalDateTime.now());

        if (action.equalsIgnoreCase("APPROVED")) {
            submission.setStatus("APPROVED");

            // Upgrade the User's KYC level!
            User targetUser = submission.getUser();
            targetUser.setKycStatus(KycStatus.APPROVED);
            targetUser.setKycLevel(2); // Bump them to level 2
            userRepository.save(targetUser);

            // Notify user of approval
            publishKycNotification(targetUser.getId(), submission.getId(), "KYC Approved",
                    "Your KYC submission has been approved. You now have Level 2 access.",
                    NotificationType.IN_APP);

        } else if (action.equalsIgnoreCase("REJECTED")) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Rejection reason is required when action is REJECTED.");
            }
            submission.setStatus("REJECTED");
            submission.setRejectionReason(reason.trim());

            // Notify user of rejection
            publishKycNotification(submission.getUser().getId(), submission.getId(), "KYC Rejected",
                    "Your KYC submission was rejected. Reason: " + reason.trim(),
                    NotificationType.IN_APP);
        } else {
            throw new IllegalArgumentException("Invalid action. Must be APPROVED or REJECTED.");
        }

        return KycResponse.fromEntity(kycRepository.save(submission));
    }

    @Transactional
    public KycResponse registerPendingSubmission(User user, KycSubmissionRequest request) {
        // Prevent multiple pending submissions
        boolean hasPending = kycRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .anyMatch(sub -> sub.getStatus().equals("PENDING"));

        if (hasPending) {
            throw new IllegalStateException("You already have a KYC submission under review.");
        }

        KycSubmission submission = KycSubmission.builder()
                .user(user)
                .smileJobId(request.getSmileJobId())
                .documentType(request.getDocumentType())
                .documentCountry(request.getDocumentCountry())
                .status("PENDING")
                .build();

        KycSubmission savedSubmission = kycRepository.save(submission);

        // Notify user that submission was received
        publishKycNotification(user.getId(), savedSubmission.getId(), "KYC Submission Received",
                "Your KYC submission is under review. We'll notify you once it's processed.",
                NotificationType.IN_APP);

        return KycResponse.fromEntity(savedSubmission);
    }

    private void publishKycNotification(UUID userId, UUID submissionId, String title, String message, NotificationType type) {
        try {
            eventPublisher.publishEvent(new TriggerNotificationEvent(
                    userId,
                    submissionId,
                    title,
                    message,
                    type,
                    java.util.Map.of("submissionId", submissionId.toString())
            ));
        } catch (Exception e) {
            // Log but don't fail the transaction
            System.err.println("Failed to publish KYC notification: " + e.getMessage());
        }
    }
}
