package com.manuelorg.cross_pesa.payment.paystack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Receives inbound Paystack webhook events.
 *
 * Security contract:
 * 1. Validate the HMAC-SHA512 {@code x-paystack-signature} header against the
 *    RAW body BEFORE any parsing — prevents spoofing and replay.
 * 2. Fast-ACK: return 200 OK as soon as the signature is valid so Paystack does
 *    not time out and retry-storm the endpoint.
 * 3. Business logic runs in {@link PaystackWebhookService} (REQUIRES_NEW).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/paystack")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final PaystackPayoutService paystackPayoutService;
    private final PaystackWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        // 1. Signature gate — constant-time HMAC-SHA512 over the raw body.
        if (!paystackPayoutService.isValidWebhookSignature(rawBody, signature)) {
            log.warn("Paystack webhook rejected: invalid or missing x-paystack-signature.");
            return ResponseEntity.status(401).build();
        }

        // 2. Parse (payload is trusted post-validation). Any failure below must not
        //    flip the HTTP status away from 200 — we ACK fast and reconcile via the
        //    settlement worker's verify call if an event is dropped.
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            String event = (String) payload.get("event");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String reference = data != null ? (String) data.get("reference") : null;

            if (event != null && reference != null) {
                webhookService.processTransferEvent(event, reference);
            } else {
                log.warn("Paystack webhook payload missing event/reference; ignoring.");
            }
        } catch (Exception e) {
            log.error("Error processing Paystack webhook: {}", e.getMessage(), e);
        }

        // 3. Fast-ACK — always 200 after successful validation.
        return ResponseEntity.ok().build();
    }
}
