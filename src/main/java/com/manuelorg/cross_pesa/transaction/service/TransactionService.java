package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.exception.DuplicateTransactionException;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.payment.paystack.PaystackPayoutService;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.systemEngine.TransactionFeeEngineService;
import com.manuelorg.cross_pesa.transaction.dto.QuoteResult;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FxRateService fxRateService;
    private final TransactionFeeEngineService feeEngine;
    private final FraudDetectionService fraudDetectionService;
    private final SystemWalletEngine systemWalletEngine;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaystackPayoutService paystackPayoutService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * 1. PROCESS SEND MONEY (External Remittance to Bank/Mobile Money)
     *
     * Order: idempotency → fraud/KYC → pessimistic lock → quote → balance check → create tx → post ledger legs
     */
    @Transactional
    public SendMoneyResponse processSendMoney(User currentUser, TransactionRequest.SendMoneyRequest request) {
        // 1. Idempotency check first — cheapest guard
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new DuplicateTransactionException("Duplicate transaction detected.");
        }

        // 2. Fraud / KYC hard validation
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        // 3. Quote FX BEFORE taking any pessimistic locks — a cache miss performs an
        //    external HTTP call, which must never run while holding wallet row locks
        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(), request.sourceCurrency().name(), request.destinationCurrency().name(),
                usdToSourceRate, sourceToDestRate
        );

        // 4. Pessimistic lock on source wallet before any balance read
        Wallet sourceWallet = walletRepository.findByIdWithLock(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to deduct from this wallet.");
        }

        Beneficiary beneficiary = beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found"));

        // 5. Available balance check under the lock — prefer ledger, fall back to wallet cache
        BigDecimal availableBalance = getAvailableBalance(sourceWallet);
        if (availableBalance.compareTo(quote.amountSent()) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + availableBalance);
        }

        // 6. Soft-flag check (velocity / high-value)
        TransactionStatus finalStatus = fraudDetectionService.isSuspiciousTransaction(
                currentUser.getId(), request.amount(), request.sourceCurrency())
                ? TransactionStatus.FLAGGED : TransactionStatus.PROCESSING;

        // 7. Create the parent Transaction record
        Transaction transaction = Transaction.builder()
                .sender(currentUser)
                .beneficiary(beneficiary)
                .sourceWallet(sourceWallet)
                .sourceCurrency(request.sourceCurrency())
                .destinationCurrency(request.destinationCurrency())
                .grossAmount(quote.amountSent())
                .netAmount(quote.amountAfterFees())
                .markupFee(quote.platformMarkupFee())
                .routingFee(quote.routingCostFee())
                .totalFee(quote.totalPlatformFee())
                .usdNormalizationRate(usdToSourceRate)
                .fxRateApplied(sourceToDestRate)
                .destinationAmount(quote.payoutAmountTarget())
                .gatewayReference("GW-OUT-" + UUID.randomUUID())
                .payoutGateway("PAYSTACK")
                .payoutReference("PSK-" + UUID.randomUUID())
                .status(finalStatus)
                .idempotencyKey(request.idempotencyKey())
                .build();

        // The existsByIdempotencyKey pre-check above is not atomic under concurrency;
        // the DB unique index on idempotency_key is the real guard. Flush forces the
        // constraint to be evaluated now so a lost race surfaces as a clean 409
        // instead of a raw constraint violation after ledger legs were posted.
        Transaction savedTransaction;
        try {
            savedTransaction = transactionRepository.save(transaction);
            transactionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTransactionException("Duplicate transaction detected.");
        }

        // 8. Immediately post all ledger legs in the same DB transaction
        systemWalletEngine.executeCrossBorderSettlement(
                savedTransaction, sourceWallet, quote.amountSent(), quote.platformMarkupFee(),
                quote.routingCostFee(), request.destinationCurrency(), quote.payoutAmountTarget(),
                quote.routingPair(), quote.markupTiersApplied(), quote.usdBaselineAmount()
        );

        // 9. Initiate the Paystack outbound transfer AFTER commit: the DB transaction
        //    (locks + ledger legs) is never held open across an external HTTP call,
        //    and a rollback can never leave an orphaned gateway transfer. If the
        //    after-commit call fails, the settlement worker's timeout reversal
        //    refunds the user — funds are never silently lost.
        if (finalStatus == TransactionStatus.PROCESSING) {
            UUID beneficiaryId = beneficiary.getId();
            String payoutReference = savedTransaction.getPayoutReference();
            BigDecimal payoutAmount = quote.payoutAmountTarget();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    initiatePaystackPayout(beneficiaryId, payoutReference, payoutAmount);
                }
            });
        } else {
            log.atWarn()
                    .addKeyValue("event", "remittance.flagged")
                    .addKeyValue("transactionId", savedTransaction.getId())
                    .log("Transaction flagged for manual review — Paystack payout NOT initiated");
        }

        log.atInfo()
                .addKeyValue("event", "remittance.initiated")
                .addKeyValue("transactionId", savedTransaction.getId())
                .addKeyValue("userId", currentUser.getId())
                .addKeyValue("amount", quote.amountSent())
                .addKeyValue("sourceCurrency", request.sourceCurrency())
                .addKeyValue("destinationCurrency", request.destinationCurrency())
                .log("Cross-border remittance initiated");

        return SendMoneyResponse.fromEntity(savedTransaction);
    }

    /**
     * After-commit hook: registers (or reuses) the Paystack transfer recipient and
     * initiates the outbound transfer. Failures are logged but never thrown back —
     * the transaction stays PROCESSING and the settlement worker reverses it on
     * timeout, so the user is always made whole.
     */
    private void initiatePaystackPayout(UUID beneficiaryId, String payoutReference, BigDecimal payoutAmount) {
        try {
            Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                    .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found: " + beneficiaryId));

            String recipientCode = paystackPayoutService.createOrGetRecipient(beneficiary);

            // Persist the recipient code in an independent REQUIRES_NEW transaction.
            // The after-commit hook runs while the just-committed EntityManager is
            // STILL bound to the thread — a default REQUIRED template would join
            // that dead session ("No active transaction"). REQUIRES_NEW forces a
            // fresh EntityManager + transaction.
            org.springframework.transaction.support.TransactionTemplate requiresNew =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(status ->
                    beneficiaryRepository.updatePaystackRecipientCode(beneficiaryId, recipientCode));
            paystackPayoutService.cacheRecipient(beneficiaryId, recipientCode);

            paystackPayoutService.initiateTransfer(
                    payoutReference, recipientCode, payoutAmount,
                    beneficiary.getAccountCurrency().name(), "Cross-Pesa payout"
            );

            log.atInfo()
                    .addKeyValue("event", "paystack.transfer.dispatched")
                    .addKeyValue("reference", payoutReference)
                    .log("Paystack outbound transfer submitted");
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("event", "paystack.transfer.dispatch_failed")
                    .addKeyValue("reference", payoutReference)
                    .addKeyValue("error", e.getMessage())
                    .log("Paystack payout dispatch failed; settlement worker will reverse on timeout", e);
        }
    }

    /**
     * Single canonical definition of "available balance": ledger-derived balance
     * minus locked funds, clamped at zero — identical semantics to
     * Wallet.getAvailableBalance().
     */
    private BigDecimal getAvailableBalance(Wallet wallet) {
        BigDecimal locked = wallet.getLockedBalance() != null ? wallet.getLockedBalance() : BigDecimal.ZERO;
        return getCurrentBalance(wallet).subtract(locked).max(BigDecimal.ZERO);
    }

    private BigDecimal getCurrentBalance(Wallet wallet) {
        return ledgerEntryRepository
                .findTopByWalletIdOrderByEntrySeqDesc(wallet.getId())
                .map(LedgerEntry::getBalanceAfter)
                .orElse(wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public Page<SendMoneyResponse> getUserTransactionHistory(UUID userId, Pageable pageable) {
        return transactionRepository.findBySenderId(userId, pageable)
                .map(SendMoneyResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public SendMoneyResponse getTransactionById(UUID userId, UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!transaction.getSender().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to transaction record.");
        }
        return SendMoneyResponse.fromEntity(transaction);
    }
}

