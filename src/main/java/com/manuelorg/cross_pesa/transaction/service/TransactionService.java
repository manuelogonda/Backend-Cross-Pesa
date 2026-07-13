package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.ledger.enums.EntryType;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.ExchangeResponse;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FxRateService fxRateService;
    private final LedgerEntryRepository ledgerEntryRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final FraudDetectionService fraudDetectionService;

    // Platform flat fee for external transfers (mocking $1.00 fee)
    private static final BigDecimal TRANSFER_FEE = new BigDecimal("1.0000");

    /**
     * 1. PROCESS SEND MONEY (External Remittance)
     * Deducts funds + fee from a user wallet and sends it to an external beneficiary.
     */

    @Transactional
    public SendMoneyResponse processSendMoney(User currentUser, TransactionRequest.SendMoneyRequest request) {
        fraudDetectionService.validateUserStatusAndKyc(currentUser, request.amount(), request.sourceCurrency());

        // 1. Idempotency Check
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected. Please verify your ledger.");
        }

        // 2. Fetch Entities
        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        Beneficiary beneficiary = beneficiaryRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found"));

        // 3. Security Check
        if (!sourceWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to deduct from this wallet.");
        }

        // 4. Balance Check (Amount + Fee)
        BigDecimal totalDeduction = request.amount().add(TRANSFER_FEE);
        if (sourceWallet.getAvailableBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + sourceWallet.getAvailableBalance());
        }

        // 5. FX Calculation
        FxRateResponse fxQuote = fxRateService.getLiveQuote(request.sourceCurrency(), request.destinationCurrency());
        BigDecimal destinationAmount = request.amount()
                .multiply(fxQuote.exchangeRate())
                .setScale(4, RoundingMode.HALF_UP);

        // NEW: Fraud Detection Check
        boolean isSuspicious = fraudDetectionService.isSuspiciousTransaction(
                currentUser.getId(),
                request.amount(),
                request.sourceCurrency()
        );

        // Determine status based on fraud rules
        TransactionStatus finalStatus = isSuspicious ? TransactionStatus.FLAGGED : TransactionStatus.PROCESSING;

        // 6. Create Transaction Record (Destination Wallet is NULL for external payouts)
        Transaction transaction = Transaction.builder()
                .sender(currentUser)
                .beneficiary(beneficiary)
                .sourceWallet(sourceWallet)
                // destinationWallet omitted - it's external!
                .sourceCurrency(request.sourceCurrency())
                .destinationCurrency(request.destinationCurrency())
                .sourceAmount(request.amount())
                .destinationAmount(destinationAmount)
                .transferFee(TRANSFER_FEE)
                .fxRateApplied(fxQuote.exchangeRate())
                .gatewayReference("GW-OUT-" + UUID.randomUUID())
                .payoutReference("PO-IN-" + UUID.randomUUID())
                .status(finalStatus) // External transfers go to processing first
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 7. Write to Ledger (Debit Only - since the credit goes to an external bank)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(savedTransaction)
                .wallet(sourceWallet)
                .entryType(EntryType.DEBIT)
                .currency(request.sourceCurrency())
                .amount(totalDeduction) // Includes the 1.00 fee
                .description("Remittance to " + beneficiary.getFirstName() + " " + beneficiary.getLastName())
                .build();

        ledgerEntryRepository.save(debitEntry);

        //notification sms africas talking
        String smsMessage = String.format(
                "Confirmed. You have successfully sent %s %s to %s %s. CrossPesa TxID: %s.",
                savedTransaction.getSourceCurrency(),
                savedTransaction.getSourceAmount(),
                beneficiary.getFirstName(),
                beneficiary.getLastName(),
                savedTransaction.getId().toString().substring(0, 8).toUpperCase()
        );

        // FIX: Removed invalid DTO wrapper syntax reference prefix
        eventPublisher.publishEvent(new TriggerNotificationEvent(
                currentUser.getId(),
                savedTransaction.getId(),
                "Transfer Successful",
                smsMessage,
                NotificationType.SMS,
                null
        ));
        // Map strictly to the SendMoneyResponse
        return TransactionResponse.SendMoneyResponse.fromEntity(savedTransaction);
    }

    /**
     * 2. PROCESS INTERNAL EXCHANGE (Wallet to Wallet FX)
     * Moves funds between a user's own wallets. No flat fee, only the FX spread applies.
     */
    @Transactional
    public ExchangeResponse processInternalExchange(User currentUser, TransactionRequest.ExchangeFundsRequest request) {

        // 1. Idempotency Check
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new IllegalStateException("Duplicate transaction detected. Please verify your ledger.");
        }

        // 2. Fetch Entities
        Wallet sourceWallet = walletRepository.findById(request.sourceWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found"));

        Wallet destinationWallet = walletRepository.findById(request.destinationWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Destination wallet not found"));

        // 3. Security Check (Must own BOTH wallets)
        if (!sourceWallet.getUser().getId().equals(currentUser.getId()) ||
                !destinationWallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not own one or both of these wallets.");
        }

        // 4. Balance Check (Just the amount, no extra fee for internal exchanges)
        BigDecimal totalDeduction = request.amount();
        if (sourceWallet.getAvailableBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalStateException("Insufficient funds for exchange.");
        }

        // 5. FX Calculation
        FxRateResponse fxQuote = fxRateService.getLiveQuote(request.sourceCurrency(), request.destinationCurrency());
        BigDecimal destinationAmount = request.amount()
                .multiply(fxQuote.exchangeRate())
                .setScale(4, RoundingMode.HALF_UP);

        // 6. Create Transaction Record (Beneficiary is NULL for internal exchanges)
        Transaction transaction = Transaction.builder()
                .sender(currentUser)
                .beneficiary(null)  // beneficiary omitted - moving to self!
                .sourceWallet(sourceWallet)
                .destinationWallet(destinationWallet)
                .sourceCurrency(request.sourceCurrency())
                .destinationCurrency(request.destinationCurrency())
                .sourceAmount(request.amount())
                .destinationAmount(destinationAmount)
                .transferFee(BigDecimal.ZERO) // No fee
                .fxRateApplied(fxQuote.exchangeRate())
                .gatewayReference("EXCH-" + UUID.randomUUID())
                .payoutReference("EXCH-" + UUID.randomUUID())
                .status(TransactionStatus.COMPLETED) // Internal exchanges clear instantly
                .idempotencyKey(request.idempotencyKey())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 7. Write to Ledger (Debit Source, Credit Destination)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(savedTransaction)
                .wallet(sourceWallet)
                .entryType(EntryType.DEBIT)
                .currency(request.sourceCurrency())
                .amount(totalDeduction)
                .description("Exchange to " + request.destinationCurrency() + " wallet")
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(savedTransaction)
                .wallet(destinationWallet)
                .entryType(EntryType.CREDIT)
                .currency(request.destinationCurrency())
                .amount(destinationAmount)
                .description("Exchange from " + request.sourceCurrency() + " wallet")
                .build();

        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));
        //notification africa's talking
        String smsMessage = String.format(
                "Exchange Confirmed. Swapped %s %s to %s %s. CrossPesa TxID: %s.",
                savedTransaction.getSourceCurrency(),
                savedTransaction.getSourceAmount(),
                savedTransaction.getDestinationAmount(),
                savedTransaction.getDestinationCurrency(),
                savedTransaction.getId().toString().substring(0, 8).toUpperCase()
        );

        eventPublisher.publishEvent(new TriggerNotificationEvent(
                currentUser.getId(),
                savedTransaction.getId(),
                "Exchange Successful",
                smsMessage,
                NotificationType.SMS,
                null
        ));

        // Map strictly to the ExchangeResponse
        return ExchangeResponse.fromEntity(savedTransaction);
    }
}

