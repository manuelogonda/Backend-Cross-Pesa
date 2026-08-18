package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.service.LedgerService;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    /**
     * Fetches the primary retail wallet belonging to a user.
     */
    @Transactional(readOnly = true)
    public WalletResponse getUserWallet(UUID userId) {
        return walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                .map(WalletResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for user ID: " + userId));
    }

    /**
     * Creates a user's primary retail wallet in their chosen currency.
     */
    @Transactional
    public WalletResponse createWallet(User user, Currency currency) {
        if (walletRepository.existsByUserIdAndWalletType(user.getId(), WalletType.USER_RETAIL)) {
            throw new IllegalStateException("User already has an active retail wallet.");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .walletType(WalletType.USER_RETAIL)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();

        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Successfully provisioned primary {} retail wallet for user ID: {}", currency, user.getId());

        return WalletResponse.fromEntity(savedWallet);
    }

    /**
     * Credits a user's retail wallet after a verified gateway deposit.
     * Fully idempotent on {@code reference}: if the same gateway reference has already been
     * processed, the existing transaction is returned without re-crediting the wallet.
     */
    @Transactional
    public WalletResponse addFunds(UUID userId, Currency currency, BigDecimal amount, String reference) {
        // 1. Input validation
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly greater than zero.");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Gateway reference must not be blank.");
        }

        // 2. Idempotency check — if this reference was already processed, return current wallet state
        if (transactionRepository.findByGatewayReference(reference).isPresent()) {
            log.info("Duplicate gateway reference '{}' detected — returning current wallet state (idempotent).", reference);
            Wallet current = walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                    .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for user ID: " + userId));
            return WalletResponse.fromEntity(current);
        }

        // 3. Pessimistic lock on the retail wallet before any mutation
        Wallet wallet = walletRepository.findByUserIdAndWalletTypeWithLock(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for user ID: " + userId));

        // 4. Validate currency match
        if (wallet.getCurrency() != currency) {
            throw new IllegalArgumentException(String.format(
                    "Currency mismatch: wallet is %s but deposit is %s.", wallet.getCurrency(), currency));
        }

        // 5. Ensure wallet is ACTIVE
        wallet.ensureActive();

        // 6. Create the top-up Transaction using builder (fees zero, rates = 1 for a direct deposit)
        Transaction topUpTx = Transaction.builder()
                .sender(wallet.getUser())
                .sourceWallet(wallet)
                .sourceCurrency(currency)
                .destinationCurrency(currency)
                .grossAmount(amount)
                .netAmount(amount)
                .destinationAmount(amount)
                .fxRateApplied(BigDecimal.ONE)
                .usdNormalizationRate(BigDecimal.ONE)
                .gatewayReference(reference)
                .status(TransactionStatus.COMPLETED)
                .idempotencyKey(UUID.randomUUID())
                .build();

        Transaction savedTransaction = transactionRepository.save(topUpTx);

        // 7. Delegate ledger entry creation and wallet balance update to LedgerService
        ledgerService.recordGatewayDeposit(savedTransaction, wallet, amount, "External Gateway Top-Up: " + reference);

        // 8. Re-fetch to return the updated balance projection
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        log.info("Credited {} {} to retail wallet of user ID: {} (ref={})", amount, currency, userId, reference);

        return WalletResponse.fromEntity(updatedWallet);
    }

    /**
     * MOCK ENDPOINT: Simulates a successful gateway top-up during testing.
     */
    @Transactional
    public WalletResponse mockTopUp(UUID userId, Currency currency, BigDecimal amount) {
        String mockReference = "MOCK-TOPUP-" + System.currentTimeMillis();
        return addFunds(userId, currency, amount, mockReference);
    }
}