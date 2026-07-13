package com.manuelorg.cross_pesa.wallet.repository;

import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    // Fetch all wallets belonging to a specific user (e.g., for the Dashboard)
    List<Wallet> findAllByUserId(UUID userId);

    // Fetch a specific wallet (e.g., getting their USD wallet to process a transfer)
    Optional<Wallet> findByUserIdAndCurrency(UUID userId, Currency currency);

    // Useful for validation before attempting to create a new wallet
    boolean existsByUserIdAndCurrency(UUID userId, Currency currency);
}