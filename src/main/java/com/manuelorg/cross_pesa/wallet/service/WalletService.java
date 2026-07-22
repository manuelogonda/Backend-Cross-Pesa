package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
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

    /**
     * Fetches the primary retail wallet belonging to a user.
     * readOnly = true optimizes Hibernate session overhead.
     */
    @Transactional(readOnly = true)
    public WalletResponse getUserWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(WalletResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found for user ID: " + userId));
    }

    /**
     * Creates a user's primary retail wallet in their chosen currency.
     */
    @Transactional
    public WalletResponse createWallet(User user, Currency currency) {
        // 1. Enforce business rule: Strict 1 retail wallet per user
        if (walletRepository.existsByUserIdAndWalletType(user.getId(), WalletType.USER_RETAIL)) {
            throw new IllegalStateException("User already has an active retail wallet.");
        }

        // 2. Build entity explicitly setting the USER_RETAIL discriminator
        Wallet wallet = Wallet.builder()
                .user(user)
                .walletType(WalletType.USER_RETAIL)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();

        // 3. Persist and map to DTO
        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Successfully provisioned primary {} retail wallet for user ID: {}", currency, user.getId());
        return WalletResponse.fromEntity(savedWallet);
    }

    /**
     * Adds funds to a user's wallet.
     * Validates that the deposit currency matches the wallet's native currency.
     */
    @Transactional
    public WalletResponse addFunds(UUID userId, Currency currency, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly greater than zero.");
        }

        // 1. Fetch the user's retail wallet
        Wallet wallet = walletRepository.findByUserId(userId)
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
            throw new IllegalStateException("Cannot add funds to a " + wallet.getStatus() + " wallet.");
        }

        // 4. Update balance
        wallet.setBalance(wallet.getBalance().add(amount));

        // 5. Save and return DTO
        Wallet updatedWallet = walletRepository.save(wallet);
        log.info("Credited {} {} to retail wallet of user ID: {}", amount, currency, userId);
        return WalletResponse.fromEntity(updatedWallet);
    }

    /**
     * MOCK ENDPOINT: Simulates a successful gateway top-up during testing.
     */
    @Transactional
    public WalletResponse mockTopUp(UUID userId, Currency currency, BigDecimal amount) {
        return addFunds(userId, currency, amount);
    }

    /**
     * System Wallet Provisioner / Lookup.
     * Ensures system operational wallets (SYSTEM_MARKUP, SYSTEM_ROUTING, SYSTEM_LIQUIDITY)
     * exist for double-entry ledger settlement.
     */
    @Transactional
    public Wallet getOrCreateSystemWallet(WalletType walletType, Currency currency, User systemUser) {
        if (walletType == WalletType.USER_RETAIL) {
            throw new IllegalArgumentException("Use createWallet for retail user accounts.");
        }

        return walletRepository.findByWalletTypeAndCurrency(walletType, currency)
                .orElseGet(() -> {
                    log.info("Auto-provisioning missing system wallet: TYPE={}, CURRENCY={}", walletType, currency);
                    Wallet systemWallet = Wallet.builder()
                            .user(systemUser)
                            .walletType(walletType)
                            .currency(currency)
                            .balance(BigDecimal.ZERO)
                            .lockedBalance(BigDecimal.ZERO)
                            .status(WalletStatus.ACTIVE)
                            .build();
                    return walletRepository.save(systemWallet);
                });
    }

    /**
     * Admin query: Paginated fetch of all system wallets.
     */
    @Transactional(readOnly = true)
    public Page<WalletResponse> getAllWallets(Pageable pageable) {
        return walletRepository.findAll(pageable)
                .map(WalletResponse::fromEntity);
    }
}