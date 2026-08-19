package com.manuelorg.cross_pesa.kycSubmission;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
import com.manuelorg.cross_pesa.kycSubmission.service.CloudinaryService;
import com.manuelorg.cross_pesa.kycSubmission.service.SmileIdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmileIdServiceTest {

    @Mock
    private KycSubmissionRepository kycRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private SmileIdService smileIdService;

    private User user;
    private KycSubmission submission;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .kycStatus(KycStatus.PENDING)
                .kycLevel(1)
                .build();

        submission = KycSubmission.builder()
                .id(UUID.randomUUID())
                .user(user)
                .smileJobId("job-001")
                .status("PENDING")
                .build();
    }

    @Test
    void processWebhook_Approved_UpdatesSubmissionAndUserToLevel2() throws IOException {
        when(kycRepository.findBySmileJobId("job-001")).thenReturn(Optional.of(submission));
        when(cloudinaryService.uploadImageFromUrl(eq("http://smile.com/id.png"), anyString()))
                .thenReturn("https://cloudinary.com/id.png");
        when(cloudinaryService.uploadImageFromUrl(eq("http://smile.com/selfie.png"), anyString()))
                .thenReturn("https://cloudinary.com/selfie.png");

        Map<String, Object> payload = new HashMap<>();
        payload.put("JobID", "job-001");
        payload.put("ResultText", "Approved");
        payload.put("ImageLinks", Map.of(
                "IDImage", "http://smile.com/id.png",
                "SelfieImage", "http://smile.com/selfie.png"
        ));

        smileIdService.processWebhook(payload);

        assertEquals("APPROVED", submission.getStatus());
        assertEquals("https://cloudinary.com/id.png", submission.getIdImageUrl());
        assertEquals("https://cloudinary.com/selfie.png", submission.getSelfieImageUrl());
        assertEquals(KycStatus.APPROVED, user.getKycStatus());
        assertEquals(2, user.getKycLevel());
        verify(userRepository).save(user);
        verify(kycRepository).save(submission);
    }

    @Test
    void processWebhook_Rejected_UpdatesSubmissionStatusAndReason() {
        when(kycRepository.findBySmileJobId("job-001")).thenReturn(Optional.of(submission));

        Map<String, Object> payload = Map.of(
                "JobID", "job-001",
                "ResultText", "Rejected"
        );

        smileIdService.processWebhook(payload);

        assertEquals("REJECTED", submission.getStatus());
        assertEquals("Automated biometric rejection by Smile ID.", submission.getRejectionReason());
        verifyNoInteractions(userRepository);
        verify(kycRepository).save(submission);
    }

    @Test
    void processWebhook_ProvisionallyApproved_LeavesPendingForAdminReview() {
        when(kycRepository.findBySmileJobId("job-001")).thenReturn(Optional.of(submission));

        Map<String, Object> payload = Map.of(
                "JobID", "job-001",
                "ResultText", "Provisionally Approved"
        );

        smileIdService.processWebhook(payload);

        assertEquals("PENDING", submission.getStatus());
        assertEquals("Requires manual admin review. Smile ID Status: Provisionally Approved", submission.getRejectionReason());
        verifyNoInteractions(userRepository);
        verify(kycRepository).save(submission);
    }

    @Test
    void processWebhook_AlreadyProcessed_SkipsIdempotently() {
        submission.setStatus("APPROVED");
        when(kycRepository.findBySmileJobId("job-001")).thenReturn(Optional.of(submission));

        Map<String, Object> payload = Map.of(
                "JobID", "job-001",
                "ResultText", "Approved"
        );

        smileIdService.processWebhook(payload);

        verify(kycRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void processWebhook_JobNotFound_LogsAndReturnsGracefully() {
        when(kycRepository.findBySmileJobId("non-existent")).thenReturn(Optional.empty());

        Map<String, Object> payload = Map.of(
                "JobID", "non-existent",
                "ResultText", "Approved"
        );

        smileIdService.processWebhook(payload);

        verify(kycRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void processWebhook_NullOrEmptyPayload_ReturnsGracefully() {
        smileIdService.processWebhook(null);
        smileIdService.processWebhook(Map.of());

        verifyNoInteractions(kycRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void processWebhook_CloudinaryUploadFails_StillSavesSubmission() throws IOException {
        when(kycRepository.findBySmileJobId("job-001")).thenReturn(Optional.of(submission));
        when(cloudinaryService.uploadImageFromUrl(anyString(), anyString()))
                .thenThrow(new RuntimeException("Cloudinary timeout"));

        Map<String, Object> payload = Map.of(
                "JobID", "job-001",
                "ResultText", "Approved",
                "ImageLinks", Map.of("IDImage", "http://smile.com/id.png")
        );

        smileIdService.processWebhook(payload);

        assertEquals("APPROVED", submission.getStatus());
        assertNull(submission.getIdImageUrl());
        verify(kycRepository).save(submission);
    }
}
