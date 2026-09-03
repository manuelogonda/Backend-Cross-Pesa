package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.payment.flutterwave.FlutterwaveTransferService;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

    private final TransactionRepository transactionRepository;
    private final SystemWalletEngine systemWalletEngine;
    private final FlutterwaveTransferService flutterwaveTransferService;
    private final ApplicationEventPublisher eventPublisher;

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
                publishNotificationAfterCommit(
                        tx.getSender().getId(),
                        tx.getId(),
                        "Transfer Completed",
                        "Your transfer of " + tx.getGrossAmount() + " " + tx.getSourceCurrency()
                                + " has been completed.",
                        Map.of(
                                "transactionId", tx.getId().toString(),
                                "status", TransactionStatus.COMPLETED.name(),
                                "payoutReference", tx.getPayoutReference()
                        )
                );
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
        publishNotificationAfterCommit(
                tx.getSender().getId(),
                tx.getId(),
                "Transfer Reversed",
                "Your transfer of " + tx.getGrossAmount() + " " + tx.getSourceCurrency()
                        + " could not be completed and has been reversed.",
                Map.of(
                        "transactionId", tx.getId().toString(),
                        "status", TransactionStatus.FAILED.name(),
                        "reason", reason
                )
        );
    }

    /**
     * Queries the external payout provider for the transfer outcome.
     *
     * FLUTTERWAVE payouts are verified via GET /transfers?reference={payoutReference}.
     * Provider status mapping:
     *   SUCCESSFUL  → CONFIRMED
     *   FAILED / CANCELLED / REVERSED → FAILED
     *   anything else (PENDING, NEW, ONGOING) or a lookup error → PENDING,
     *   so the existing timeout reversal remains the safety net.
     */
    private PayoutStatus checkExternalPayoutStatus(Transaction tx) {
        if (!"FLUTTERWAVE".equalsIgnoreCase(tx.getPayoutGateway())) {
            return PayoutStatus.PENDING;
        }
        String providerStatus = flutterwaveTransferService.verifyTransferStatus(tx.getPayoutReference());
        if (providerStatus == null) {
            return PayoutStatus.PENDING;
        }
        return switch (providerStatus.toUpperCase()) {
            case "SUCCESSFUL" -> PayoutStatus.CONFIRMED;
            case "FAILED", "CANCELLED", "REVERSED" -> PayoutStatus.FAILED;
            default -> PayoutStatus.PENDING;
        };
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
