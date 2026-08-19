package com.manuelorg.cross_pesa.kycSubmission;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycResponse;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycSubmissionRequest;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
import com.manuelorg.cross_pesa.kycSubmission.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private KycSubmissionRepository kycRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KycService kycService;

    private User user;
    private User adminUser;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .kycStatus(KycStatus.PENDING)
                .kycLevel(1)
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .build();
    }

    @Test
    void registerPendingSubmission_WhenNoPending_CreatesSuccessfully() {
        KycSubmissionRequest request = new KycSubmissionRequest();
        request.setSmileJobId("job-123");
        request.setDocumentType("PASSPORT");
        request.setDocumentCountry("KE");

        when(kycRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(kycRepository.save(any(KycSubmission.class))).thenAnswer(invocation -> {
            KycSubmission sub = invocation.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        KycResponse response = kycService.registerPendingSubmission(user, request);

        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals("PASSPORT", response.getDocumentType());
        assertEquals("KE", response.getDocumentCountry());
        verify(kycRepository).save(any(KycSubmission.class));
    }

    @Test
    void registerPendingSubmission_WhenAlreadyPending_ThrowsIllegalStateException() {
        KycSubmission existing = KycSubmission.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status("PENDING")
                .build();

        when(kycRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(existing));

        KycSubmissionRequest request = new KycSubmissionRequest();
        request.setSmileJobId("job-456");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> kycService.registerPendingSubmission(user, request));
        assertTrue(ex.getMessage().contains("already have a KYC submission under review"));
        verify(kycRepository, never()).save(any(KycSubmission.class));
    }

    @Test
    void reviewSubmission_Approve_UpdatesSubmissionAndUser() {
        UUID submissionId = UUID.randomUUID();
        KycSubmission submission = KycSubmission.builder()
                .id(submissionId)
                .user(user)
                .status("PENDING")
                .build();

        when(kycRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(kycRepository.save(any(KycSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycResponse response = kycService.reviewSubmission(submissionId, "APPROVED", null, adminUser);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(KycStatus.APPROVED, user.getKycStatus());
        assertEquals(2, user.getKycLevel());
        assertEquals(adminUser, submission.getReviewedBy());
        assertNotNull(submission.getReviewedAt());
        verify(userRepository).save(user);
        verify(kycRepository).save(submission);
    }

    @Test
    void reviewSubmission_Reject_WithReason_UpdatesStatus() {
        UUID submissionId = UUID.randomUUID();
        KycSubmission submission = KycSubmission.builder()
                .id(submissionId)
                .user(user)
                .status("PENDING")
                .build();

        when(kycRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(kycRepository.save(any(KycSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycResponse response = kycService.reviewSubmission(submissionId, "REJECTED", "Document is blurry", adminUser);

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Document is blurry", response.getRejectionReason());
        verifyNoInteractions(userRepository);
        verify(kycRepository).save(submission);
    }

    @Test
    void reviewSubmission_Reject_WithoutReason_ThrowsIllegalArgumentException() {
        UUID submissionId = UUID.randomUUID();
        KycSubmission submission = KycSubmission.builder()
                .id(submissionId)
                .user(user)
                .status("PENDING")
                .build();

        when(kycRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> kycService.reviewSubmission(submissionId, "REJECTED", "   ", adminUser));
        assertTrue(ex.getMessage().contains("Rejection reason is required"));
    }

    @Test
    void reviewSubmission_AlreadyProcessed_ThrowsIllegalStateException() {
        UUID submissionId = UUID.randomUUID();
        KycSubmission submission = KycSubmission.builder()
                .id(submissionId)
                .user(user)
                .status("APPROVED")
                .build();

        when(kycRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(IllegalStateException.class,
                () -> kycService.reviewSubmission(submissionId, "REJECTED", "Reason", adminUser));
    }

    @Test
    void getAllSubmissions_WithStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        KycSubmission submission = KycSubmission.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status("PENDING")
                .build();

        when(kycRepository.findByStatus("PENDING", pageable)).thenReturn(new PageImpl<>(List.of(submission)));

        Page<KycResponse> page = kycService.getAllSubmissions("pending", pageable);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        verify(kycRepository).findByStatus("PENDING", pageable);
    }
}
