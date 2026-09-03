package com.manuelorg.cross_pesa.transaction.controller;

import com.manuelorg.cross_pesa.config.WebhookSecurityService;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final TransactionRepository transactionRepository;
    private final WebhookSecurityService webhookSecurityService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Receives payout status callbacks from the external payment gateway.
     * POST /api/v1/webhooks/payout-update
     *
     * Security: requests must carry a valid HMAC-SHA256 signature of the raw
     * body in the {@code X-Webhook-Signature} header. Unsigned or invalidly
     * signed calls are rejected with 401 before any state is read.
     */
    @PostMapping("/payout-update")
    @Transactional
    public ResponseEntity<String> handleGatewayCallback(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        // 1. Signature gate — reject before doing any work
        if (!webhookSecurityService.isValidPayoutSignature(rawBody, signature)) {
            log.warn("Rejected payout webhook with missing/invalid signature");
            return ResponseEntity.status(401).body("Invalid webhook signature");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Malformed JSON body");
        }

        // 2. Extract the reference ID sent back by the gateway
        String gatewayReference = payload.get("reference") instanceof String s ? s : null;
        String gatewayStatus = payload.get("status") instanceof String s ? s : null;

        if (gatewayReference == null || gatewayStatus == null) {
            return ResponseEntity.badRequest().body("Missing 'reference' or 'status' field");
        }

        // 3. Find the pending transaction
        Transaction transaction = transactionRepository.findByGatewayReference(gatewayReference)
                .orElse(null);
        if (transaction == null) {
            // Do not reveal whether the reference exists; respond idempotently OK
            log.warn("Payout webhook for unknown reference ignored");
            return ResponseEntity.ok("Ignored");
        }

        // 4. The State Machine Logic — only PROCESSING transactions may transition
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            return ResponseEntity.ok("Already processed");
        }

        TransactionStatus newStatus = switch (gatewayStatus.toUpperCase()) {
            case "SUCCESS" -> TransactionStatus.COMPLETED;
            case "FAILED" -> TransactionStatus.FAILED;
            case "PROCESSING" -> TransactionStatus.PROCESSING;
            default -> null;
        };

        if (newStatus == null) {
            return ResponseEntity.badRequest().body("Unknown status value");
        }

        transaction.setStatus(newStatus);
        if (newStatus == TransactionStatus.FAILED) {
            // TODO: write a Reversal Ledger Entry here to refund the user's wallet
            log.error("Payout FAILED for reference {} — reversal entry pending implementation", gatewayReference);
            publishNotificationAfterCommit(
                    transaction.getSender().getId(),
                    transaction.getId(),
                    "Payout Failed",
                    "Your payout of " + transaction.getGrossAmount() + " " + transaction.getSourceCurrency()
                            + " failed and is pending reversal.",
                    Map.of(
                            "transactionId", transaction.getId().toString(),
                            "status", TransactionStatus.FAILED.name(),
                            "gatewayReference", gatewayReference,
                            "gatewayStatus", gatewayStatus
                    )
            );
        } else if (newStatus == TransactionStatus.COMPLETED) {
            publishNotificationAfterCommit(
                    transaction.getSender().getId(),
                    transaction.getId(),
                    "Payout Completed",
                    "Your payout of " + transaction.getGrossAmount() + " " + transaction.getSourceCurrency()
                            + " has been completed.",
                    Map.of(
                            "transactionId", transaction.getId().toString(),
                            "status", TransactionStatus.COMPLETED.name(),
                            "gatewayReference", gatewayReference
                    )
            );
        }
        transactionRepository.save(transaction);

        return ResponseEntity.ok("State updated successfully");
    }

    private void publishNotificationAfterCommit(
            UUID userId,
            UUID transactionId,
            String title,
            String message,
            Map<String, Object> metadata
    ) {
        Runnable publish = () -> eventPublisher.publishEvent(new TriggerNotificationEvent(
                userId,
                transactionId,
                title,
                message,
                NotificationType.IN_APP,
                metadata
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }

        publish.run();
    }
}
