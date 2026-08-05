package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.systemEngine.TransactionFeeEngineService;
import com.manuelorg.cross_pesa.transaction.dto.QuoteResult;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.ExchangeResponse;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final TransactionFeeEngineService feeEngine;
    private final FraudDetectionService fraudDetectionService;
    private final ApplicationEventPublisher eventPublisher;

    // Injected the tools needed for Ledger writes
    private final SystemWalletEngine systemWalletEngine;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EntityManager entityManager;

    /**
     * 1. PROCESS SEND MONEY (External Remittance to Bank/Mobile Money)
     */
    @Transactional
    public SendMoneyResponse processSendMoney(User currentUser, TransactionRequest.SendMoneyRequest request) {
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }

        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to deduct from this wallet.");
        }

        Beneficiary beneficiary = beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found"));

        // Get Live FX Rates
        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        // Run Pricing Engine
        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(), request.sourceCurrency().name(), request.destinationCurrency().name(),
                usdToSourceRate, sourceToDestRate
        );

        if (sourceWallet.getAvailableBalance().compareTo(quote.amountSent()) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + sourceWallet.getAvailableBalance());
        }

        TransactionStatus finalStatus = fraudDetectionService.isSuspiciousTransaction(
                currentUser.getId(), request.amount(), request.sourceCurrency())
                ? TransactionStatus.FLAGGED : TransactionStatus.PROCESSING;

        // Create the Parent Transaction
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

        // DELEGATE to the SystemWalletEngine for the 6-leg outward ledger
        systemWalletEngine.executeCrossBorderSettlement(
                savedTransaction, sourceWallet, quote.amountSent(), quote.platformMarkupFee(),
                quote.routingCostFee(), request.destinationCurrency(), quote.payoutAmountTarget(),
                quote.routingPair(), quote.markupTiersApplied(), quote.usdBaselineAmount()
        );

        log.info("Cross-Border Remittance Sent: {} {} to {}", quote.amountSent(), request.sourceCurrency(), beneficiary.getFirstName());

        return SendMoneyResponse.fromEntity(savedTransaction);
    }

    /**
     * 2. PROCESS PEER-TO-PEER (P2P) TRANSFER (Internal Wallet-to-Wallet)
     */
    @Transactional
    public ExchangeResponse processPeerToPeerTransfer(User currentUser, TransactionRequest.ExchangeFundsRequest request) {

        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }


        Wallet sourceWallet = walletRepository.findByIdWithLock(request.sourceWalletId())
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

        // Process internal 8-Leg P2P Ledger directly
        recordP2PLedgerEntries(savedTransaction, sourceWallet, destinationWallet, quote);

        return ExchangeResponse.fromEntity(savedTransaction);
    }

    /**
     * ===================================================================================
     * P2P DOUBLE-ENTRY LEDGER ENGINE (8-Leg Sequence)
     * ===================================================================================
     */
    private void recordP2PLedgerEntries(Transaction tx, Wallet sourceWallet, Wallet destWallet, QuoteResult quote) {
        List<LedgerEntry> entries = new ArrayList<>();

        Wallet systemMarkup = systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_MARKUP);
        Wallet systemRouting = systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_ROUTING);
        Wallet systemLiquiditySource = systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_LIQUIDITY);
        Wallet systemLiquidityDest = systemWalletEngine.getSystemWallet(tx.getDestinationCurrency(), WalletType.SYSTEM_LIQUIDITY);

        // 1. DEDUCT from Sender User
        entries.add(buildEntry(tx, sourceWallet, EntryClass.PRINCIPAL_TRANSFER, quote.amountSent(), BigDecimal.ZERO, quote, "P2P Sent"));

        if (quote.platformMarkupFee().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildEntry(tx, sourceWallet, EntryClass.MARKUP_FEE, quote.platformMarkupFee(), BigDecimal.ZERO, quote, "P2P Markup"));
            entries.add(buildEntry(tx, systemMarkup, EntryClass.MARKUP_FEE, BigDecimal.ZERO, quote.platformMarkupFee(), quote, "P2P Margin Credit"));
        }
        if (quote.routingCostFee().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildEntry(tx, sourceWallet, EntryClass.ROUTING_FEE, quote.routingCostFee(), BigDecimal.ZERO, quote, "P2P Routing Cost"));
            entries.add(buildEntry(tx, systemRouting, EntryClass.ROUTING_FEE, BigDecimal.ZERO, quote.routingCostFee(), quote, "P2P Routing Liability"));
        }

        // 2. Clear Source Currency INTO Liquidity Pool
        entries.add(buildEntry(tx, systemLiquiditySource, EntryClass.FX_CLEARING, BigDecimal.ZERO, quote.amountAfterFees(), quote, "P2P Inbound Clearing"));

        // 3. Clear Target Currency OUT of Liquidity Pool
        entries.add(buildEntry(tx, systemLiquidityDest, EntryClass.FX_CLEARING, quote.payoutAmountTarget(), BigDecimal.ZERO, quote, "P2P Outbound Clearing"));

        // 4. CREDIT to Receiver User
        entries.add(buildEntry(tx, destWallet, EntryClass.PRINCIPAL_TRANSFER, BigDecimal.ZERO, quote.payoutAmountTarget(), quote, "P2P Received"));

        ledgerEntryRepository.saveAll(entries);

        // CRITICAL: Flush to force PostgreSQL triggers to calculate User Balances
        entityManager.flush();
        entityManager.clear();
    }

    private LedgerEntry buildEntry(Transaction tx, Wallet wallet, EntryClass entryClass, BigDecimal debit, BigDecimal credit, QuoteResult quote, String desc) {
        return LedgerEntry.builder()
                .transaction(tx)
                .wallet(wallet)
                .entryClass(entryClass) // Maps to the SQL constraint
                .debit(debit)
                .credit(credit)
                .currency(wallet.getCurrency())
                .description(desc)
                .usdBaselineAmount(quote.usdBaselineAmount())
                .routingPair(quote.routingPair())
                .markupTiersApplied(quote.markupTiersApplied())
                .build();
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

