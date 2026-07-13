package com.manuelorg.cross_pesa.transaction.controller;

import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final TransactionRepository transactionRepository;

    /**
     * Receives callbacks from your external payment gateway.
     * POST /api/v1/webhooks/payout-update
     */
    @PostMapping("/payout-update")
    @Transactional
    public ResponseEntity<String> handleGatewayCallback(@RequestBody Map<String, Object> payload) {
        // 1. Extract the reference ID sent back by the gateway
        String gatewayReference = (String) payload.get("reference");
        String gatewayStatus = (String) payload.get("status"); // e.g., "SUCCESS" or "FAILED"

        // 2. Find the pending transaction
        Transaction transaction = transactionRepository.findByGatewayReference(gatewayReference)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction reference"));

        // 3. The State Machine Logic
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return ResponseEntity.ok("Already processed");
        }

        switch (gatewayStatus.toUpperCase()) {
            case "SUCCESS":
                transaction.setStatus(TransactionStatus.COMPLETED);
                break;
            case "FAILED":
                transaction.setStatus(TransactionStatus.FAILED);
                // Note: If failed, you would also write a Reversal Ledger Entry here to refund the user's wallet!
                break;
            case "PROCESSING":
                transaction.setStatus(TransactionStatus.PROCESSING);
                break;
        }

        transactionRepository.save(transaction);
        return ResponseEntity.ok("State updated successfully");
    }
}
