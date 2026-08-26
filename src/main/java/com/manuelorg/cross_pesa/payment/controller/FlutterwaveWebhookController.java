package com.manuelorg.cross_pesa.payment.controller;

import com.manuelorg.cross_pesa.config.observability.TraceIdFilter;
import com.manuelorg.cross_pesa.payment.dto.FlutterwaveWebhookPayload;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveService;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Single unified inbound webhook endpoint for Flutterwave events.
 *
 * <p>Security contract (mandatory per architecture rules):
 * <ol>
 *   <li>Validate the {@code verif-hash} signature header FIRST.</li>
 *   <li>Fast-ACK: return HTTP 200 OK immediately after validation so
 *       Flutterwave does not retry-storm the endpoint.</li>
 *   <li>Business logic runs in {@link FlutterwaveWebhookService} inside its own
 *       REQUIRES_NEW transaction, decoupled from the HTTP response.</li>
 * </ol>
 *
 * <p>Handled events:
 * <ul>
 *   <li>{@code charge.completed} — wallet top-ups (inbound collections).</li>
 *   <li>{@code transfer.completed} — outbound payout confirmed.</li>
 *   <li>{@code transfer.failed} / {@code transfer.reversed} — compensating
 *       ledger reversal refunding the user's wallet.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/flutterwave")
@RequiredArgsConstructor
public class FlutterwaveWebhookController {

    private final FlutterwaveService flutterwaveService;
    private final FlutterwaveWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "verif-hash", required = false) String verifHash,
            @RequestBody String rawBody
    ) {
        // 1. Signature gate — constant-time comparison against the shared secret.
        if (!flutterwaveService.isValidWebhookSignature(verifHash)) {
            log.warn("Flutterwave webhook rejected: invalid or missing verif-hash header.");
            return ResponseEntity.status(401).build();
        }

        // 2. Fast-ACK — parse/process failures must never flip this to an error,
        //    otherwise Flutterwave retries and we lose the single-ACK contract.
        try {
            FlutterwaveWebhookPayload payload = objectMapper.readValue(rawBody, FlutterwaveWebhookPayload.class);
            String event = payload.event();
            String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);

            if (event == null || payload.data() == null) {
                log.warn("Flutterwave webhook payload missing event/data; ignoring.");
                return ResponseEntity.ok().build();
            }

            switch (event) {
                case "charge.completed" -> webhookService.processChargeEvent(payload);
                case "transfer.completed", "transfer.failed", "transfer.reversed" ->
                        // The event name alone is NOT authoritative: Flutterwave fires
                        // transfer.completed even when data.status is FAILED or REVERSED
                        // (destination bank / mobile-money switch rejected after queueing).
                        // Success is decided strictly by data.status inside the service.
                        webhookService.processTransferEvent(payload.data().reference(), payload.data().status(), traceId);
                default -> log.atInfo()
                        .addKeyValue("event", "flutterwave.webhook.unhandled")
                        .addKeyValue("flutterwaveEvent", event)
                        .log("Unhandled Flutterwave webhook event type");
            }
        } catch (Exception e) {
            log.error("Error processing Flutterwave webhook payload: {}", e.getMessage(), e);
        }

        // 3. Fast-ACK — always 200 after successful validation.
        return ResponseEntity.ok().build();
    }
}
