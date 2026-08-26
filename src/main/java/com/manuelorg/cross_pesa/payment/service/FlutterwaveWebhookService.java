package com.manuelorg.cross_pesa.payment.service;

import com.manuelorg.cross_pesa.payment.dto.FlutterwaveWebhookPayload;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processes inbound Flutterwave webhook events.
 *
 * All methods are fully idempotent: calling them more than once with the same
 * payload produces the same result without double-crediting the wallet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlutterwaveWebhookService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final SystemWalletEngine systemWalletEngine;

    /**
     * Processes a {@code charge.completed} (or equivalent) webhook event.
     *
     * <p>Flow:
     * <ol>
     *   <li>Look up our Transaction by {@code tx_ref} (gateway reference).</li>
     *   <li>If already COMPLETED or FAILED → no-op (idempotent guard).</li>
     *   <li>On {@code successful} charge → credit wallet via
     *       {@link WalletService#addFunds} (which is itself idempotent on the reference)
     *       and mark the Transaction COMPLETED.</li>
     *   <li>On any other status → mark the Transaction FAILED.</li>
     * </ol>
     */
    @Transactional
    public void processChargeEvent(FlutterwaveWebhookPayload payload) {
        if (payload == null || payload.data() == null) {
            log.warn("Received null or empty webhook payload — ignoring.");
            return;
        }

        FlutterwaveWebhookPayload.Data data = payload.data();
        String txRef = data.txRef();

        if (txRef == null || txRef.isBlank()) {
            log.warn("Webhook payload missing tx_ref — ignoring. event={}", payload.event());
            return;
        }

        log.info("Processing webhook event={} txRef={} status={}", payload.event(), txRef, data.status());

        Optional<Transaction> txOpt = transactionRepository.findByGatewayReference(txRef);

        if (txOpt.isEmpty()) {
            // Could be a payment initiated outside our system; log and ignore.
            log.warn("No Transaction found for gatewayReference='{}' — ignoring webhook.", txRef);
            return;
        }

        Transaction transaction = txOpt.get();

        // Idempotency guard: terminal states must not be re-processed.
        if (transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED) {
            log.info("Transaction {} is already in terminal state {} — skipping webhook processing.",
                    transaction.getId(), transaction.getStatus());
            return;
        }

        if ("successful".equalsIgnoreCase(data.status())) {
            handleSuccessfulCharge(transaction, data);
        } else {
            handleFailedCharge(transaction, data);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleSuccessfulCharge(Transaction transaction, FlutterwaveWebhookPayload.Data data) {
        if (transaction.getSourceWallet() == null || transaction.getSourceWallet().getUser() == null) {
            log.error("Transaction {} has no source wallet or user — cannot credit. Marking FAILED.",
                    transaction.getId());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            return;
        }

        Currency currency;
        try {
            currency = Currency.valueOf(data.currency().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.error("Unsupported currency '{}' in webhook for txRef={} — marking FAILED.",
                    data.currency(), data.txRef());
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            return;
        }

        // WalletService.addFunds is idempotent on the gateway reference (txRef).
        // If this webhook fires twice, the second call is a no-op.
        walletService.addFunds(
                transaction.getSourceWallet().getUser().getId(),
                currency,
                data.amount(),
                data.txRef()
        );

        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        log.info("Webhook: credited {} {} to user {} for txRef={}.",
                data.amount(), currency,
                transaction.getSourceWallet().getUser().getId(),
                data.txRef());
    }

    private void handleFailedCharge(Transaction transaction, FlutterwaveWebhookPayload.Data data) {
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        log.info("Webhook: marked Transaction {} as FAILED (gateway status='{}', txRef={}).",
                transaction.getId(), data.status(), data.txRef());
    }

    // -------------------------------------------------------------------------
    // Outbound transfer events (payouts)
    // -------------------------------------------------------------------------

    /**
     * Handles {@code transfer.completed} / {@code transfer.failed} /
     * {@code transfer.reversed} events keyed on our internal payout reference.
     *
     * <p>Runs in its own transaction (REQUIRES_NEW) so the HTTP Fast-ACK response
     * stays decoupled from business processing. Idempotent: terminal-state guard
     * means replayed events are no-ops; the REFUND entry-class uniqueness check
     * inside {@code executePayoutReversal} prevents double reversals under a race.
     *
     * @param providerStatus raw gateway status string from the webhook data
     *                      (SUCCESSFUL / FAILED / REVERSED / PENDING / NEW);
     *                      only SUCCESSFUL confirms a payout, anything else
     *                      triggers the compensating ledger reversal
     * @param traceId inbound MDC trace id propagated from the controller thread,
     *                since the webhook thread may differ from the request thread
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTransferEvent(String payoutReference, String providerStatus, String traceId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            if (payoutReference == null || payoutReference.isBlank()) {
                log.warn("Flutterwave transfer webhook missing reference; ignoring.");
                return;
            }

            Transaction tx = transactionRepository.findByPayoutReference(payoutReference).orElse(null);
            if (tx == null) {
                log.warn("Flutterwave transfer webhook for unknown payout reference {}; ignoring.", payoutReference);
                return;
            }

            // Only non-terminal states may transition.
            if (tx.getStatus() != TransactionStatus.PROCESSING && tx.getStatus() != TransactionStatus.PENDING) {
                log.atInfo()
                        .addKeyValue("event", "flutterwave.webhook.skipped")
                        .addKeyValue("transactionId", tx.getId())
                        .addKeyValue("currentStatus", tx.getStatus().name())
                        .log("Transfer webhook received for transaction already in terminal status");
                return;
            }

            boolean successful = "successful".equalsIgnoreCase(
                    providerStatus == null ? "" : providerStatus.trim());
            if (successful) {
                confirmPayout(tx);
            } else {
                log.atWarn()
                        .addKeyValue("event", "flutterwave.webhook.negative_status")
                        .addKeyValue("transactionId", tx.getId())
                        .addKeyValue("providerStatus", providerStatus)
                        .log("Non-successful Flutterway transfer status — triggering reversal");
                reversePayout(tx, providerStatus);
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    /** transfer.completed: ledger was committed at initiation — mark COMPLETED. */
    private void confirmPayout(Transaction tx) {
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        log.atInfo()
                .addKeyValue("event", "payout.confirmed")
                .addKeyValue("transactionId", tx.getId())
                .addKeyValue("provider", "FLUTTERWAVE")
                .log("Flutterwave transfer confirmed by webhook — marked COMPLETED");
    }

    /** transfer.failed / reversed: compensating ledger reversal refunds the user. */
    private void reversePayout(Transaction tx, String providerStatus) {
        log.atWarn()
                .addKeyValue("event", "payout.reversal.triggered")
                .addKeyValue("transactionId", tx.getId())
                .addKeyValue("providerStatus", providerStatus)
                .log("Reversing settlement and refunding user wallet");

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
}
