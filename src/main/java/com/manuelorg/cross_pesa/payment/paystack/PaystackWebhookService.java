package com.manuelorg.cross_pesa.payment.paystack;

import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes validated Paystack transfer webhook events.
 *
 * Runs in its own transaction (REQUIRES_NEW) so the HTTP Fast-ACK response is
 * decoupled from business processing, and so a single event failure never
 * rolls back unrelated work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaystackWebhookService {

    private final TransactionRepository transactionRepository;
    private final SystemWalletEngine systemWalletEngine;

    /**
     * Handles {@code transfer.success}, {@code transfer.failed} and
     * {@code transfer.reversed} events keyed on our internal payout reference.
     *
     * Idempotency: terminal-state guard means replayed events are no-ops; the
     * REFUND entry-class uniqueness check inside executePayoutReversal prevents
     * double reversals even under a race.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTransferEvent(String event, String reference) {
        Transaction tx = transactionRepository.findByPayoutReference(reference).orElse(null);
        if (tx == null) {
            log.warn("Paystack webhook for unknown payout reference {}; ignoring.", reference);
            return;
        }

        // Only non-terminal states may transition.
        if (tx.getStatus() != TransactionStatus.PROCESSING && tx.getStatus() != TransactionStatus.PENDING) {
            log.atInfo()
                    .addKeyValue("event", "paystack.webhook.skipped")
                    .addKeyValue("transactionId", tx.getId())
                    .addKeyValue("currentStatus", tx.getStatus().name())
                    .log("Webhook received for transaction already in terminal status");
            return;
        }

        switch (event) {
            case "transfer.success" -> confirmPayout(tx);
            case "transfer.failed", "transfer.reversed" -> reversePayout(tx, event);
            default -> log.atInfo()
                    .addKeyValue("event", "paystack.webhook.unhandled")
                    .addKeyValue("paystackEvent", event)
                    .log("Unhandled Paystack webhook event type");
        }
    }

    /** transfer.success: ledger was committed at initiation — mark COMPLETED. */
    private void confirmPayout(Transaction tx) {
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        log.atInfo()
                .addKeyValue("event", "payout.confirmed")
                .addKeyValue("transactionId", tx.getId())
                .addKeyValue("provider", "PAYSTACK")
                .log("Paystack transfer confirmed by webhook — marked COMPLETED");
    }

    /** transfer.failed / reversed: compensating ledger reversal refunds the user. */
    private void reversePayout(Transaction tx, String paystackEvent) {
        log.atWarn()
                .addKeyValue("event", "payout.reversal.triggered")
                .addKeyValue("transactionId", tx.getId())
                .addKeyValue("paystackEvent", paystackEvent)
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
