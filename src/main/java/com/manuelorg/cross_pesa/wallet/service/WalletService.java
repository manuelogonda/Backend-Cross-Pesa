package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    /**
     * Fetches all wallets for a user and maps them to DTOs.
     * readOnly = true optimizes the Hibernate session since we aren't modifying data.
     */
    @Transactional(readOnly = true)
    public List<WalletResponse> getUserWallets(UUID userId) {
        return walletRepository.findAllByUserId(userId)
                .stream()
                .map(WalletResponse::fromEntity)
                .toList(); // Available in Java 16+
    }

    /**
     * Creates a new currency wallet for a user.
     * Standard @Transactional ensures the save operation is atomic.
     */
    @Transactional
    public WalletResponse createWallet(User user, Currency currency) {
        // 1. Enforce the business rule: One wallet per currency per user
        if (walletRepository.existsByUserIdAndCurrency(user.getId(), currency)) {
            throw new IllegalArgumentException("Wallet for currency " + currency + " already exists.");
        }

        // 2. Build the wallet. Balances and status are handled by @Builder.Default in the Entity.
        var wallet = Wallet.builder()
                .user(user)
                .currency(currency)
                .build();

        // 3. Persist to DB
        Wallet savedWallet = walletRepository.save(wallet);

        // 4. Return the safe DTO to the controller
        return WalletResponse.fromEntity(savedWallet);
    }

    /**
     * MOCK ENDPOINT: Simulates a successful payment gateway top-up.
     * In a production environment, this would be a webhook triggered by M-Pesa or Stripe.
     */
    @Transactional
    public WalletResponse mockTopUp(UUID userId, Currency currency, BigDecimal amount) {
        // 1. Find the specific wallet
        Wallet wallet = walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for currency: " + currency));

        // 2. Prevent top-ups on frozen or suspended wallets
        if (!wallet.getStatus().name().equals("ACTIVE")) {
            throw new IllegalStateException("Cannot top up a " + wallet.getStatus() + " wallet.");
        }

        // 3. Add the funds
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);

        // 4. Save and return updated state
        Wallet updatedWallet = walletRepository.save(wallet);
        return WalletResponse.fromEntity(updatedWallet);
    }
}