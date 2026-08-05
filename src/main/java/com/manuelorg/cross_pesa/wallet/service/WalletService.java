package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
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
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService; // 🟢 Inject LedgerService instead of LedgerEntryRepository
    private final EntityManager entityManager;

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
     * Adds funds to a user's wallet via the LedgerService.
     */
    @Transactional
    public WalletResponse addFunds(UUID userId, Currency currency, BigDecimal amount, String reference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly greater than zero.");
        }

        // 1. Fetch the user's retail wallet with a lock to prevent race conditions during concurrent top-ups
        Wallet wallet = walletRepository.findByUserIdAndWalletTypeWithLock(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for user ID: " + userId));

        // 2. Validate Currency Match
        if (wallet.getCurrency() != currency) {
            throw new IllegalArgumentException(String.format(
                    "Currency mismatch: Wallet is in %s, but attempted deposit was in %s.",
                    wallet.getCurrency(), currency
            ));
        }

        // 3. Ensure the wallet is active
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot add funds to a " + wallet.getStatus() + " wallet.");
        }

        // 4. Create the Top-Up Transaction Record
        Transaction topUpTx = new Transaction();
        topUpTx.setSender(wallet.getUser());
        topUpTx.setSourceWallet(wallet);
        topUpTx.setSourceCurrency(currency);
        topUpTx.setDestinationCurrency(currency);
        topUpTx.setGrossAmount(amount);
        topUpTx.setNetAmount(amount);
        topUpTx.setDestinationAmount(amount);
        topUpTx.setFxRateApplied(BigDecimal.ONE);
        topUpTx.setUsdNormalizationRate(BigDecimal.ONE);
        topUpTx.setGatewayReference(reference);
        topUpTx.setStatus(TransactionStatus.COMPLETED);
        topUpTx.setIdempotencyKey(UUID.randomUUID());

        Transaction savedTransaction = transactionRepository.save(topUpTx);

        // 5. DELEGATE ledger entry creation and balance mutation to LedgerService
        ledgerService.recordGatewayDeposit(savedTransaction, wallet, amount, "External Gateway Top-Up: " + reference);

        // 6. Re-fetch the wallet to return the updated DTO with the new balance
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        log.info("Ledger injected: Credited {} {} to retail wallet of user ID: {}", amount, currency, userId);

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