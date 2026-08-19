package com.manuelorg.cross_pesa.kycSubmission.service;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmileIdService {

    private final KycSubmissionRepository kycRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public void processWebhook(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            log.warn("Received empty or null Smile ID webhook payload");
            return;
        }

        try {
            // 1. Extract core data from the Smile ID payload
            String jobId = (String) payload.get("JobID");
            if (jobId == null || jobId.isBlank()) {
                log.warn("Smile ID Webhook payload missing JobID");
                return;
            }

            String resultText = (String) payload.get("ResultText"); // Usually "Approved", "Rejected", or "Provisionally Approved"

            log.info("Processing Smile ID Webhook for Job: {}", jobId);

            // 2. Find the pending submission in our database
            KycSubmission submission = kycRepository.findBySmileJobId(jobId)
                    .orElse(null);

            if (submission == null) {
                log.warn("No KYC submission found for Job ID: {}", jobId);
                return;
            }

            // Prevent processing the same webhook twice
            if (!"PENDING".equalsIgnoreCase(submission.getStatus())) {
                log.warn("Job {} is already processed. Current status: {}", jobId, submission.getStatus());
                return;
            }

            // 3. Extract Image Links (Smile ID sends these in an 'ImageLinks' object)
            // Note: Safely casting nested maps to avoid NullPointerExceptions
            Object imageLinksObj = payload.get("ImageLinks");
            if (imageLinksObj instanceof Map<?, ?> rawImageLinks) {
                String smileSelfieUrl = rawImageLinks.get("SelfieImage") instanceof String s ? s : null;
                String smileIdUrl = rawImageLinks.get("IDImage") instanceof String s ? s : null;

                // 4. Permanently store images in Cloudinary so they don't expire
                if (smileIdUrl != null && !smileIdUrl.isBlank()) {
                    try {
                        String permanentIdUrl = cloudinaryService.uploadImageFromUrl(smileIdUrl, "cross_pesa_kyc/ids");
                        submission.setIdImageUrl(permanentIdUrl);
                    } catch (Exception e) {
                        log.error("Failed to upload ID image to Cloudinary for Job ID: {}", jobId, e);
                    }
                }

                if (smileSelfieUrl != null && !smileSelfieUrl.isBlank()) {
                    try {
                        String permanentSelfieUrl = cloudinaryService.uploadImageFromUrl(smileSelfieUrl, "cross_pesa_kyc/selfies");
                        submission.setSelfieImageUrl(permanentSelfieUrl);
                    } catch (Exception e) {
                        log.error("Failed to upload selfie image to Cloudinary for Job ID: {}", jobId, e);
                    }
                }
            }

            // 5. Apply Business Logic based on Smile ID's biometric result
            if ("Approved".equalsIgnoreCase(resultText)) {
                // Auto-Approve the submission
                submission.setStatus("APPROVED");

                // Upgrade the User's KYC Level automatically!
                User user = submission.getUser();
                if (user != null) {
                    user.setKycStatus(KycStatus.APPROVED);
                    user.setKycLevel(2);
                    userRepository.save(user);
                    log.info("Auto-approved KYC for user: {}", user.getEmail());
                }

            } else if ("Rejected".equalsIgnoreCase(resultText)) {
                // Auto-Reject the submission
                submission.setStatus("REJECTED");
                submission.setRejectionReason("Automated biometric rejection by Smile ID.");
                log.info("Auto-rejected KYC for Job: {}", jobId);
            } else {
                // For edge cases (e.g., blurry images), leave it as PENDING for the Admin Dashboard to review
                submission.setRejectionReason("Requires manual admin review. Smile ID Status: " + resultText);
                log.info("Job {} requires manual admin review.", jobId);
            }

            // 6. Save the final state to PostgreSQL
            kycRepository.save(submission);

        } catch (Exception e) {
            log.error("Failed to process Smile ID Webhook", e);
        }
    }
}
