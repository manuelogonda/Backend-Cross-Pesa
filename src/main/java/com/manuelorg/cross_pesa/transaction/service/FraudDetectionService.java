package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final FxRateService fxRateService;

    // Thresholds
    private static final BigDecimal MAX_TRANSACTION_LIMIT_KES = new BigDecimal("100000.00");
    private static final int MAX_TRANSACTIONS_PER_HOUR = 5;

    /**
     * Hard blocks: Rejects the transaction completely if rules are violated.
     */
    public void validateUserStatusAndKyc(User user, BigDecimal amount, Currency sourceCurrency) {
        // 1. Block Suspended/Locked Accounts
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.LOCKED) {
            log.warn("Blocked transfer attempt from {} account: {}", user.getStatus(), user.getId());
            throw new SecurityException("Your account is currently " + user.getStatus() + ". Transactions are disabled.");
        }

        // 2. Block Unverified Users from sending money entirely
        if (user.getKycStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException("Your KYC profile is " + user.getKycStatus() + ". You must be APPROVED to send money.");
        }

        // 3. Convert to Base Currency (KES) for Limit Checking
        BigDecimal amountInKes = amount;
        if (sourceCurrency != Currency.KES) {
            // FIX: Added .name() to match FxRateService string parameters
            FxRateResponse rateResponse = fxRateService.getLiveQuote(sourceCurrency.name(), Currency.KES.name());
            amountInKes = amount.multiply(rateResponse.exchangeRate());
        }

        // 4. Enforce Tiered KYC Limits — against the DAILY AGGREGATE, not just
        //    this single transaction (a tier-1 user could otherwise send 50x 49k)
        BigDecimal dailyLimit = switch (user.getKycLevel()) {
            case 1 -> new BigDecimal("50000.00");   // Basic ID - 50k KES limit
            case 2 -> new BigDecimal("500000.00");  // Verified Address - 500k KES limit
            case 3 -> new BigDecimal("5000000.00"); // Corporate/Premium - 5M KES limit
            default -> BigDecimal.ZERO;
        };

        OffsetDateTime startOfDay = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        BigDecimal sentToday = transactionRepository.sumSentSince(user.getId(), startOfDay);
        BigDecimal dailyTotal = sentToday.add(amountInKes);

        if (dailyTotal.compareTo(dailyLimit) > 0) {
            log.warn("KYC Daily Limit Exceeded: User {} (Tier {}) attempted transfer pushing daily total to {} KES.",
                    user.getId(), user.getKycLevel(), dailyTotal);
            throw new IllegalStateException("This transfer would exceed your Tier " + user.getKycLevel()
                    + " daily limit of " + dailyLimit + " KES.");
        }
    }

    /**
     * Evaluates a transaction against fraud rules.
     * Returns TRUE if the transaction is suspicious (FLAGGED), FALSE if it is safe.
     */
    @Transactional(readOnly = true)
    public boolean isSuspiciousTransaction(UUID userId, BigDecimal amount, Currency sourceCurrency) {
        boolean isFlagged = false;

        // Rule 1: High Value Transaction Check (> 100k KES)
        BigDecimal amountInKes = amount;
        if (sourceCurrency != Currency.KES) {
            // FIX: Added .name() to match FxRateService string parameters
            FxRateResponse rateResponse = fxRateService.getLiveQuote(sourceCurrency.name(), Currency.KES.name());
            amountInKes = amount.multiply(rateResponse.exchangeRate());
        }

        if (amountInKes.compareTo(MAX_TRANSACTION_LIMIT_KES) > 0) {
            log.warn("FRAUD ALERT: User {} attempted a high-value transfer of {} {}", userId, amount, sourceCurrency);
            isFlagged = true;
        }

        // Rule 2: Velocity Check (More than 5 times in the last hour)
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long recentTransactions = transactionRepository.countRecentTransactionsByUser(userId, oneHourAgo);

        if (recentTransactions >= MAX_TRANSACTIONS_PER_HOUR) {
            log.warn("FRAUD ALERT: User {} exceeded velocity limit. {} transactions in the last hour.", userId, recentTransactions);
            isFlagged = true;
        }

        return isFlagged;
    }
}
