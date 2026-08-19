package com.manuelorg.cross_pesa.kycSubmission;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.kycSubmission.controller.KycController;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycActionRequest;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycResponse;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycSubmissionRequest;
import com.manuelorg.cross_pesa.kycSubmission.service.KycService;
import com.manuelorg.cross_pesa.kycSubmission.service.SmileIdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycControllerTest {

    @Mock
    private KycService kycService;

    @Mock
    private SmileIdService smileIdService;

    @InjectMocks
    private KycController kycController;

    private User currentUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .build();
    }

    @Test
    void getMyHistory_ReturnsUserSubmissions() {
        KycResponse kyc = KycResponse.builder()
                .id(UUID.randomUUID())
                .status("PENDING")
                .build();

        when(kycService.getUserSubmissions(currentUser.getId())).thenReturn(List.of(kyc));

        ResponseEntity<List<KycResponse>> response = kycController.getMyHistory(currentUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(kycService).getUserSubmissions(currentUser.getId());
    }

    @Test
    void getSubmissionsForAdmin_ReturnsPagedSubmissions() {
        KycResponse kyc = KycResponse.builder()
                .id(UUID.randomUUID())
                .status("PENDING")
                .build();
        Page<KycResponse> page = new PageImpl<>(List.of(kyc));

        when(kycService.getAllSubmissions(eq("PENDING"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<KycResponse>> response = kycController.getSubmissionsForAdmin("PENDING", 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void reviewSubmission_CallsServiceAndReturnsResult() {
        UUID submissionId = UUID.randomUUID();
        KycActionRequest request = new KycActionRequest();
        request.setAction("APPROVED");
        request.setReason(null);

        KycResponse kycResponse = KycResponse.builder()
                .id(submissionId)
                .status("APPROVED")
                .build();

        when(kycService.reviewSubmission(submissionId, "APPROVED", null, adminUser))
                .thenReturn(kycResponse);

        ResponseEntity<KycResponse> response = kycController.reviewSubmission(submissionId, request, adminUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("APPROVED", response.getBody().getStatus());
        verify(kycService).reviewSubmission(submissionId, "APPROVED", null, adminUser);
    }

    @Test
    void submitKyc_ReturnsCreated() {
        KycSubmissionRequest request = new KycSubmissionRequest();
        request.setSmileJobId("job-999");
        request.setDocumentType("PASSPORT");
        request.setDocumentCountry("KE");

        KycResponse kycResponse = KycResponse.builder()
                .id(UUID.randomUUID())
                .status("PENDING")
                .build();

        when(kycService.registerPendingSubmission(currentUser, request)).thenReturn(kycResponse);

        ResponseEntity<KycResponse> response = kycController.submitKyc(request, currentUser);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PENDING", response.getBody().getStatus());
        verify(kycService).registerPendingSubmission(currentUser, request);
    }

    @Test
    void handleSmileIdWebhook_ProcessesWebhookAndReturns200() {
        Map<String, Object> payload = Map.of("JobID", "job-123");

        ResponseEntity<String> response = kycController.handleSmileIdWebhook(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook received and processed", response.getBody());
        verify(smileIdService).processWebhook(payload);
    }
}
