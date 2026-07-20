package com.manuelorg.cross_pesa.kycSubmission.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycActionRequest;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycResponse;
import com.manuelorg.cross_pesa.kycSubmission.dto.KycSubmissionRequest;
import com.manuelorg.cross_pesa.kycSubmission.service.KycService;
import com.manuelorg.cross_pesa.kycSubmission.service.SmileIdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;
    private final SmileIdService smileIdService;

    // --- USER ENDPOINTS ---

    @GetMapping("/my-history")
    public ResponseEntity<List<KycResponse>> getMyHistory(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(kycService.getUserSubmissions(currentUser.getId()));
    }

    // --- ADMIN ENDPOINTS ---

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/submissions")
    public ResponseEntity<Page<KycResponse>> getSubmissionsForAdmin(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Sort by newest first
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(kycService.getAllSubmissions(status, pageRequest));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/submissions/{id}/review")
    public ResponseEntity<KycResponse> reviewSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody KycActionRequest request,
            @AuthenticationPrincipal User adminUser) {

        return ResponseEntity.ok(kycService.reviewSubmission(id, request.getAction(), request.getReason(), adminUser));
    }

    @PostMapping("/submit")
    public ResponseEntity<KycResponse> submitKyc(
            @Valid @RequestBody KycSubmissionRequest request,
            @AuthenticationPrincipal User currentUser) {

        KycResponse response = kycService.registerPendingSubmission(currentUser, request);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * PUBLIC ENDPOINT: Smile ID Webhook Listener
     * Smile ID servers hit this URL asynchronously after processing the images.
     */
    @PostMapping("/webhook/smile-id")
    public ResponseEntity<String> handleSmileIdWebhook(@RequestBody Map<String, Object> payload) {

        // Pass the raw JSON map straight to our dedicated service
        smileIdService.processWebhook(payload);

        // Always return 200 OK fast. If you don't return 200, Smile ID will keep retrying!
        return ResponseEntity.ok("Webhook received and processed");
    }
}
