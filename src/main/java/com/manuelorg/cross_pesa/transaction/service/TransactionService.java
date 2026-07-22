package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.transaction.dto.QuoteResult;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.ExchangeResponse;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FxRateService fxRateService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionFeeEngineService feeEngine;
    private final FraudDetectionService fraudDetectionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 1. PROCESS SEND MONEY (External Remittance)
     */
    @Transactional
    public SendMoneyResponse processSendMoney(User currentUser, TransactionRequest.SendMoneyRequest request) {
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }

        // Fetch Source Wallet (Using ID from DTO) & Verify Ownership
        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to deduct from this wallet.");
        }

        Beneficiary beneficiary = beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found"));

        // Get Live FX Rates (Convert Enum to String using .name())
        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        // Run the Fee Engine
        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(),
                request.sourceCurrency().name(),
                request.destinationCurrency().name(),
                usdToSourceRate,
                sourceToDestRate
        );

        // Balance Check against the Gross Amount
        if (sourceWallet.getAvailableBalance().compareTo(quote.amountSent()) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + sourceWallet.getAvailableBalance());
        }

        TransactionStatus finalStatus = fraudDetectionService.isSuspiciousTransaction(
                currentUser.getId(), request.amount(), request.sourceCurrency())
                ? TransactionStatus.FLAGGED : TransactionStatus.PROCESSING;

        // Create the Transaction
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
                .payoutReference("PO-IN-" + UUID.randomUUID())
                .status(finalStatus)
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Record perfectly balanced double-entry ledger logs
        recordLedgerEntries(savedTransaction, sourceWallet, null, quote);

        // Send SMS
        String smsMessage = String.format("Confirmed. Sent %s %s to %s %s. TxID: %s.",
                savedTransaction.getSourceCurrency(), savedTransaction.getGrossAmount(),
                beneficiary.getFirstName(), beneficiary.getLastName(),
                savedTransaction.getId().toString().substring(0, 8).toUpperCase());

        // eventPublisher.publishEvent(new TriggerNotificationEvent(...)); // Keep your event publisher here

        return SendMoneyResponse.fromEntity(savedTransaction);
    }

    /**
     * 2. PROCESS PEER-TO-PEER (P2P) TRANSFER
     */
    @Transactional
    public ExchangeResponse processPeerToPeerTransfer(User currentUser, TransactionRequest.ExchangeFundsRequest request) {

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }

        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not own the source wallet.");
        }

        Wallet destinationWallet = walletRepository.findById(request.destinationWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient wallet not found"));

        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(), request.sourceCurrency().name(), request.destinationCurrency().name(), usdToSourceRate, sourceToDestRate
        );

        if (sourceWallet.getAvailableBalance().compareTo(quote.amountSent()) < 0) {
            throw new IllegalStateException("Insufficient funds for P2P transfer.");
        }

        Transaction transaction = Transaction.builder()
                .sender(currentUser)
                .sourceWallet(sourceWallet)
                .destinationWallet(destinationWallet)
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
                .status(TransactionStatus.COMPLETED)
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Record perfectly balanced double-entry ledger logs
        recordLedgerEntries(savedTransaction, sourceWallet, destinationWallet, quote);

        return ExchangeResponse.fromEntity(savedTransaction);
    }

    /**
     * ===================================================================================
     * THE DOUBLE-ENTRY LEDGER ENGINE
     * Ensures Debits exactly equal Credits for every currency involved.
     * ===================================================================================
     */
    private void recordLedgerEntries(Transaction tx, Wallet sourceWallet, Wallet destWallet, QuoteResult quote) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Fetch System Wallets (Requires adding these find methods to your WalletRepository)
        Wallet systemMarkupWallet = walletRepository.findByWalletTypeAndCurrency(WalletType.SYSTEM_MARKUP, tx.getSourceCurrency())
                .orElseThrow(() -> new IllegalStateException("Missing System Markup Wallet for " + tx.getSourceCurrency()));

        Wallet systemRoutingWallet = walletRepository.findByWalletTypeAndCurrency(WalletType.SYSTEM_ROUTING, tx.getSourceCurrency())
                .orElseThrow(() -> new IllegalStateException("Missing System Routing Wallet for " + tx.getSourceCurrency()));

        Wallet systemLiquiditySource = walletRepository.findByWalletTypeAndCurrency(WalletType.SYSTEM_LIQUIDITY, tx.getSourceCurrency())
                .orElseThrow(() -> new IllegalStateException("Missing System Liquidity Wallet for " + tx.getSourceCurrency()));

        String pair = tx.getSourceCurrency().name() + "_" + tx.getDestinationCurrency().name();

        // ----------------------------------------------------------------
        // LEG 1: SOURCE CURRENCY BALANCE (Debits = Credits)
        // ----------------------------------------------------------------
        // DEBIT 1: Deduct Gross Amount from User
        entries.add(buildEntry(tx, sourceWallet, "PRINCIPAL_TRANSFER", quote.amountSent(), BigDecimal.ZERO, "Sent to " + pair));

        // CREDIT 1: Platform Profit
        if (quote.platformMarkupFee().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildEntry(tx, systemMarkupWallet, "MARKUP_FEE", BigDecimal.ZERO, quote.platformMarkupFee(), "Margin on " + tx.getId()));
        }

        // CREDIT 2: Corridor Infrastructure Cost
        if (quote.routingCostFee().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildEntry(tx, systemRoutingWallet, "ROUTING_FEE", BigDecimal.ZERO, quote.routingCostFee(), "Cost for " + pair));
        }

        // CREDIT 3: Move Net Funds to AfriPay Liquidity Pool (FX Clearing)
        entries.add(buildEntry(tx, systemLiquiditySource, "FX_CLEARING", BigDecimal.ZERO, quote.amountAfterFees(), "Inbound FX Clearing"));

        // ----------------------------------------------------------------
        // LEG 2: DESTINATION CURRENCY BALANCE (For P2P Only)
        // ----------------------------------------------------------------
        if (destWallet != null) {
            Wallet systemLiquidityDest = walletRepository.findByWalletTypeAndCurrency(WalletType.SYSTEM_LIQUIDITY, tx.getDestinationCurrency())
                    .orElseThrow(() -> new IllegalStateException("Missing System Liquidity Wallet for " + tx.getDestinationCurrency()));

            // DEBIT 4: Deduct from AfriPay's Target Currency Pool
            entries.add(buildEntry(tx, systemLiquidityDest, "FX_CLEARING", quote.payoutAmountTarget(), BigDecimal.ZERO, "Outbound FX Clearing"));

            // CREDIT 4: Credit the Receiver's Wallet
            entries.add(buildEntry(tx, destWallet, "PRINCIPAL_TRANSFER", BigDecimal.ZERO, quote.payoutAmountTarget(), "Received P2P Transfer"));
        }

        ledgerEntryRepository.saveAll(entries);
    }

    private LedgerEntry buildEntry(Transaction tx, Wallet wallet, String entryClass, BigDecimal debit, BigDecimal credit, String desc) {
        return LedgerEntry.builder()
                .transaction(tx)
                .wallet(wallet)
                .entryClass(entryClass) // Assuming you mapped EntryClass as a String or Enum
                .debit(debit)
                .credit(credit)
                .currency(wallet.getCurrency())
                .description(desc)
                .usdBaselineAmount(tx.getUsdNormalizationRate()) // Audit tie-back
                .routingPair(tx.getSourceCurrency().name() + "_" + tx.getDestinationCurrency().name())
                .build();
    }
    /**
     * Retrieves paginated transaction history for the authenticated user.
     */
    @Transactional(readOnly = true)
    public Page<SendMoneyResponse> getUserTransactionHistory(UUID userId, Pageable pageable) {
        return transactionRepository.findBySenderId(userId, pageable)
                .map(SendMoneyResponse::fromEntity);
    }

    /**
     * Fetches a single transaction by ID, ensuring ownership validation.
     */
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

