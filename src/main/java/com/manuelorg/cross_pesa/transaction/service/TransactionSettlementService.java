package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

    private final TransactionRepository transactionRepository;
    private final EntityManager entityManager;

    /**
     * Confirms external payout status and moves the transaction to COMPLETED or FAILED.
     *
     * Ledger legs were already posted atomically at initiation time (in TransactionService.processSendMoney).
     * This worker MUST NOT call executeCrossBorderSettlement again — doing so would double-post all ledger legs.
     *
     * Each reconciliation runs in its own transaction (REQUIRES_NEW) so a single failure
     * does not roll back the entire batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSingleTransaction(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        // Guard: only process PROCESSING or PENDING transactions
        if (tx.getStatus() != TransactionStatus.PROCESSING && tx.getStatus() != TransactionStatus.PENDING) {
            log.debug("Transaction {} is already in terminal status {}; skipping.", transactionId, tx.getStatus());
            return;
        }

        // Grace period: give the external gateway at least 1 minute before polling
        if (tx.getCreatedAt().plusMinutes(1).isAfter(OffsetDateTime.now())) {
            log.debug("Transaction {} is within grace period; deferring reconciliation.", transactionId);
            return;
        }

        boolean externalGatewaySuccess = checkExternalPayoutStatus(tx);

        if (externalGatewaySuccess) {
            tx.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(tx);
            log.info("Transaction {} confirmed by external gateway — marked COMPLETED.", tx.getId());
        } else {
            log.warn("Transaction {} payout still processing at external provider.", tx.getId());
        }

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Queries the external payment provider to confirm whether the payout was delivered.
     * Replace this stub with a real Flutterwave / M-Pesa / Paystack verify call.
     */
    private boolean checkExternalPayoutStatus(Transaction tx) {
        // Stub: treat any transaction older than 1 minute as confirmed by the external gateway.
        // Production: call provider SDK with tx.getPayoutReference() and verify the response.
        return tx.getCreatedAt().plusMinutes(1).isBefore(OffsetDateTime.now());
    }
}
