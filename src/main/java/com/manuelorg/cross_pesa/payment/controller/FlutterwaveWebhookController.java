package com.manuelorg.cross_pesa.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuelorg.cross_pesa.payment.dto.FlutterwaveWebhookPayload;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveService;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives inbound Flutterwave webhook events.
 *
 * Security contract (mandatory per architecture rules):
 * 1. Validate the {@code verif-hash} signature FIRST.
 * 2. Return HTTP 200 OK immediately after validation.
 * 3. Delegate business logic to {@link FlutterwaveWebhookService} (separate transactional method).
 *
 * This endpoint must be excluded from CSRF protection and JWT authentication
 * in {@code SecurityConfig} (it is called by Flutterwave's servers, not our users).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/flutterwave")
@RequiredArgsConstructor
public class FlutterwaveWebhookController {

    private final FlutterwaveService flutterwaveService;
    private final FlutterwaveWebhookService webhookService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/v1/webhooks/flutterwave
     *
     * @param verifHash the {@code verif-hash} header sent by Flutterwave
     * @param rawBody   the raw JSON body (kept as String so we can validate before parsing)
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "verif-hash", required = false) String verifHash,
            @RequestBody String rawBody
    ) {
        // 1. Validate signature — reject immediately if invalid.
        if (!isValidSignature(verifHash)) {
            log.warn("Webhook rejected: invalid or missing verif-hash header.");
            return ResponseEntity.status(401).build();
        }

        // 2. Return 200 OK immediately — Flutterwave expects a fast acknowledgement.
        //    Business logic runs in a separate transactional call below.
        log.info("Webhook signature validated. Acknowledging receipt.");

        // 3. Parse and process (still within the same thread but in its own transaction).
        try {
            FlutterwaveWebhookPayload payload = objectMapper.readValue(rawBody, FlutterwaveWebhookPayload.class);
            webhookService.processChargeEvent(payload);
        } catch (Exception e) {
            // Log the error but do NOT change the HTTP response — we already committed 200.
            // Flutterwave will not retry if we return 200, so we must handle failures internally.
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates the Flutterwave webhook signature.
     * Delegates to {@link FlutterwaveService#isValidWebhookSignature} for constant-time comparison.
     */
    private boolean isValidSignature(String verifHash) {
        return flutterwaveService.isValidWebhookSignature(verifHash);
    }
}
