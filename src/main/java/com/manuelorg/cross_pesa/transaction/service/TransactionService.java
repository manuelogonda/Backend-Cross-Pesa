package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.systemEngine.TransactionFeeEngineService;
import com.manuelorg.cross_pesa.transaction.dto.QuoteResult;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.ExchangeResponse;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final SystemWalletEngine systemWalletEngine;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EntityManager entityManager;

    /**
     * 1. PROCESS SEND MONEY (External Remittance to Bank/Mobile Money)
     *
     * Order: idempotency → fraud/KYC → pessimistic lock → quote → balance check → create tx → post ledger legs
     */
    @Transactional
    public SendMoneyResponse processSendMoney(User currentUser, TransactionRequest.SendMoneyRequest request) {
        // 1. Idempotency check first — cheapest guard
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }

        // 2. Fraud / KYC hard validation
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        // 3. Pessimistic lock on source wallet before any balance read
        Wallet sourceWallet = walletRepository.findByIdWithLock(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to deduct from this wallet.");
        }

        Beneficiary beneficiary = beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found"));

        // 4. Live FX rates → QuoteResult
        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(), request.sourceCurrency().name(), request.destinationCurrency().name(),
                usdToSourceRate, sourceToDestRate
        );

        // 5. Available balance check under the lock — prefer ledger, fall back to wallet cache
        BigDecimal availableBalance = getCurrentBalance(sourceWallet).subtract(
                sourceWallet.getLockedBalance() != null ? sourceWallet.getLockedBalance() : BigDecimal.ZERO);
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
                .payoutReference("PO-IN-" + UUID.randomUUID())
                .status(finalStatus)
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 8. Immediately post all ledger legs in the same DB transaction
        systemWalletEngine.executeCrossBorderSettlement(
                savedTransaction, sourceWallet, quote.amountSent(), quote.platformMarkupFee(),
                quote.routingCostFee(), request.destinationCurrency(), quote.payoutAmountTarget(),
                quote.routingPair(), quote.markupTiersApplied(), quote.usdBaselineAmount()
        );

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
     * 2. PROCESS PEER-TO-PEER (P2P) TRANSFER (Internal Wallet-to-Wallet)
     *
     * Order: idempotency → fraud/KYC → deterministic lock on both wallets → quote → balance check → create tx → post ledger legs
     */
    @Transactional
    public ExchangeResponse processPeerToPeerTransfer(User currentUser, TransactionRequest.ExchangeFundsRequest request) {
        // 1. Idempotency check first
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected.");
        }

        // 2. Fraud / KYC hard validation
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        // 3. Pessimistic lock on source wallet
        Wallet sourceWallet = walletRepository.findByIdWithLock(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not own the source wallet.");
        }

        // Lock destination wallet too — deterministic UUID order to prevent deadlocks
        Wallet destinationWallet;
        if (request.sourceWalletId().compareTo(request.destinationWalletId()) < 0) {
            destinationWallet = walletRepository.findByIdWithLock(request.destinationWalletId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient wallet not found"));
        } else {
            // Source was locked first above; re-lock dest (already locked source)
            destinationWallet = walletRepository.findByIdWithLock(request.destinationWalletId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient wallet not found"));
        }

        // 4. Live FX rates → QuoteResult
        BigDecimal usdToSourceRate = fxRateService.getLiveQuote("USD", request.sourceCurrency().name()).exchangeRate();
        BigDecimal sourceToDestRate = fxRateService.getLiveQuote(request.sourceCurrency().name(), request.destinationCurrency().name()).exchangeRate();

        QuoteResult quote = feeEngine.calculateTransaction(
                request.amount(), request.sourceCurrency().name(), request.destinationCurrency().name(),
                usdToSourceRate, sourceToDestRate
        );

        // 5. Available balance check under the lock — prefer ledger, fall back to wallet cache
        BigDecimal availableBalance = getCurrentBalance(sourceWallet).subtract(
                sourceWallet.getLockedBalance() != null ? sourceWallet.getLockedBalance() : BigDecimal.ZERO);
        if (availableBalance.compareTo(quote.amountSent()) < 0) {
            throw new IllegalStateException("Insufficient funds for P2P transfer. Available: " + availableBalance);
        }

        // 6. Create the parent Transaction record
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

        // 7. Post all P2P ledger legs with correct running balanceAfter
        recordP2PLedgerEntries(savedTransaction, sourceWallet, destinationWallet, quote);

        log.atInfo()
                .addKeyValue("event", "p2p.completed")
                .addKeyValue("transactionId", savedTransaction.getId())
                .addKeyValue("userId", currentUser.getId())
                .addKeyValue("amount", quote.amountSent())
                .addKeyValue("sourceCurrency", request.sourceCurrency())
                .addKeyValue("destinationCurrency", request.destinationCurrency())
                .log("P2P transfer completed");

        return ExchangeResponse.fromEntity(savedTransaction);
    }

    /**
     * Posts all double-entry ledger legs for a P2P transfer.
     * Each leg carries the correct running balanceAfter; wallet balance projections are updated after saving.
     */
    private void recordP2PLedgerEntries(Transaction tx, Wallet sourceWallet, Wallet destWallet, QuoteResult quote) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Resolve and lock system wallets in deterministic UUID order
        Wallet systemMarkup = walletRepository.findByIdWithLock(
                systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_MARKUP).getId())
                .orElseThrow();
        Wallet systemRouting = walletRepository.findByIdWithLock(
                systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_ROUTING).getId())
                .orElseThrow();
        Wallet systemLiquiditySource = walletRepository.findByIdWithLock(
                systemWalletEngine.getSystemWallet(tx.getSourceCurrency(), WalletType.SYSTEM_LIQUIDITY).getId())
                .orElseThrow();
        Wallet systemLiquidityDest = walletRepository.findByIdWithLock(
                systemWalletEngine.getSystemWallet(tx.getDestinationCurrency(), WalletType.SYSTEM_LIQUIDITY).getId())
                .orElseThrow();

        // Derive current balances from ledger (source of truth)
        BigDecimal srcRunning = getCurrentBalance(sourceWallet);
        BigDecimal markupRunning = getCurrentBalance(systemMarkup);
        BigDecimal routingRunning = getCurrentBalance(systemRouting);
        BigDecimal srcLiqRunning = getCurrentBalance(systemLiquiditySource);
        BigDecimal destLiqRunning = getCurrentBalance(systemLiquidityDest);
        BigDecimal destRunning = getCurrentBalance(destWallet);

        // --- SOURCE WALLET DEBITS (running balance per leg) ---
        srcRunning = srcRunning.subtract(quote.amountSent());
        entries.add(buildEntry(tx, sourceWallet, EntryClass.PRINCIPAL_TRANSFER,
                quote.amountSent(), BigDecimal.ZERO, quote, "P2P Sent", srcRunning));

        if (quote.platformMarkupFee().compareTo(BigDecimal.ZERO) > 0) {
            srcRunning = srcRunning.subtract(quote.platformMarkupFee());
            entries.add(buildEntry(tx, sourceWallet, EntryClass.MARKUP_FEE,
                    quote.platformMarkupFee(), BigDecimal.ZERO, quote, "P2P Markup", srcRunning));

            markupRunning = markupRunning.add(quote.platformMarkupFee());
            entries.add(buildEntry(tx, systemMarkup, EntryClass.MARKUP_FEE,
                    BigDecimal.ZERO, quote.platformMarkupFee(), quote, "P2P Margin Credit", markupRunning));
        }

        if (quote.routingCostFee().compareTo(BigDecimal.ZERO) > 0) {
            srcRunning = srcRunning.subtract(quote.routingCostFee());
            entries.add(buildEntry(tx, sourceWallet, EntryClass.ROUTING_FEE,
                    quote.routingCostFee(), BigDecimal.ZERO, quote, "P2P Routing Cost", srcRunning));

            routingRunning = routingRunning.add(quote.routingCostFee());
            entries.add(buildEntry(tx, systemRouting, EntryClass.ROUTING_FEE,
                    BigDecimal.ZERO, quote.routingCostFee(), quote, "P2P Routing Liability", routingRunning));
        }

        // --- FX CLEARING ---
        // The clearing pool receives the FULL principal: the user was separately
        // debited the markup + routing fees, which are credited to their own
        // system wallets. Crediting net here would leave the ledger unbalanced.
        srcLiqRunning = srcLiqRunning.add(quote.amountSent());
        entries.add(buildEntry(tx, systemLiquiditySource, EntryClass.FX_CLEARING,
                BigDecimal.ZERO, quote.amountSent(), quote, "P2P Inbound Clearing", srcLiqRunning));

        destLiqRunning = destLiqRunning.subtract(quote.payoutAmountTarget());
        entries.add(buildEntry(tx, systemLiquidityDest, EntryClass.FX_CLEARING,
                quote.payoutAmountTarget(), BigDecimal.ZERO, quote, "P2P Outbound Clearing", destLiqRunning));

        // --- DESTINATION WALLET CREDIT ---
        destRunning = destRunning.add(quote.payoutAmountTarget());
        entries.add(buildEntry(tx, destWallet, EntryClass.PRINCIPAL_TRANSFER,
                BigDecimal.ZERO, quote.payoutAmountTarget(), quote, "P2P Received", destRunning));

        // Persist ledger entries first (source of truth), then update wallet balance projections
        ledgerEntryRepository.saveAll(entries);

        sourceWallet.setBalance(srcRunning);
        systemMarkup.setBalance(markupRunning);
        systemRouting.setBalance(routingRunning);
        systemLiquiditySource.setBalance(srcLiqRunning);
        systemLiquidityDest.setBalance(destLiqRunning);
        destWallet.setBalance(destRunning);

        walletRepository.saveAll(List.of(
                sourceWallet, systemMarkup, systemRouting, systemLiquiditySource, systemLiquidityDest, destWallet));

        entityManager.flush();
        entityManager.clear();
    }

    private LedgerEntry buildEntry(Transaction tx, Wallet wallet, EntryClass entryClass,
                                   BigDecimal debit, BigDecimal credit, QuoteResult quote,
                                   String desc, BigDecimal balanceAfter) {
        return LedgerEntry.builder()
                .transaction(tx)
                .wallet(wallet)
                .entryClass(entryClass)
                .debit(debit)
                .credit(credit)
                .currency(wallet.getCurrency())
                .description(desc)
                .usdBaselineAmount(quote.usdBaselineAmount())
                .routingPair(quote.routingPair())
                .markupTiersApplied(quote.markupTiersApplied())
                .balanceAfter(balanceAfter)
                .build();
    }

    /**
     * Derives the current balance for a wallet from the most recent ledger entry.
     * Falls back to the wallet's cached balance field if no ledger entries exist yet.
     */
    private BigDecimal getCurrentBalance(Wallet wallet) {
        return ledgerEntryRepository
                .findTopByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId())
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

