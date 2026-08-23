package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final SystemWalletEngine systemWalletEngine;

    /**
     * How long a PROCESSING payout may remain unconfirmed before we treat it as failed,
     * reverse the ledger, and refund the customer.
     */
    @Value("${settlement.payout-timeout-minutes:60}")
    private long payoutTimeoutMinutes;

    /** Outcome of an external payout status enquiry. */
    private enum PayoutStatus { CONFIRMED, FAILED, PENDING }

    /**
     * Confirms external payout status and moves the transaction to COMPLETED or FAILED.
     *
     * Ledger legs were already posted atomically at initiation time (in TransactionService.processSendMoney).
     * This worker MUST NOT call executeCrossBorderSettlement again — doing so would double-post all ledger legs.
     *
     * On failure (explicitly reported by the provider, or timed out without confirmation) the full
     * settlement is reversed via {@link SystemWalletEngine#executePayoutReversal}, refunding the user
     * and restoring the system float wallets so the books stay balanced.
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

        PayoutStatus providerStatus = checkExternalPayoutStatus(tx);

        switch (providerStatus) {
            case CONFIRMED -> {
                tx.setStatus(TransactionStatus.COMPLETED);
                transactionRepository.save(tx);
                log.info("Transaction {} confirmed by external gateway — marked COMPLETED.", tx.getId());
            }
            case FAILED -> failAndReverse(tx, "Provider reported payout failure");
            case PENDING -> {
                // Never auto-complete without provider confirmation. If the payout has been
                // unconfirmed beyond the timeout window, treat it as failed and refund.
                if (tx.getCreatedAt().plusMinutes(payoutTimeoutMinutes).isBefore(OffsetDateTime.now())) {
                    failAndReverse(tx, "Payout unconfirmed by provider for over " + payoutTimeoutMinutes + " minutes");
                } else {
                    log.debug("Transaction {} payout still processing at external provider; deferring.", tx.getId());
                }
            }
        }
    }

    private void failAndReverse(Transaction tx, String reason) {
        log.warn("Failing transaction {} and reversing settlement: {}", tx.getId(), reason);
        systemWalletEngine.executePayoutReversal(
                tx,
                tx.getSourceWallet().getId(),
                tx.getSourceCurrency(),
                tx.getGrossAmount(),
                tx.getMarkupFee(),
                tx.getRoutingFee(),
                tx.getDestinationCurrency(),
                tx.getDestinationAmount(),
                null
        );
        tx.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(tx);
    }

    /**
     * Queries the external payment provider for the payout outcome.
     *
     * TODO: Replace with a real Flutterwave / M-Pesa / Paystack transfer-status call using
     * tx.getPayoutReference(). Until a provider integration exists this returns PENDING,
     * meaning payouts will be reversed (refunded) once the timeout elapses — never silently
     * marked COMPLETED like the previous stub did.
     */
    private PayoutStatus checkExternalPayoutStatus(Transaction tx) {
        return PayoutStatus.PENDING;
    }
}
