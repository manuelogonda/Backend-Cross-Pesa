package com.manuelorg.cross_pesa.wallet.repository;

import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    // --- RETAIL WALLET LOOKUPS ---

    /**
     * Fetches the user's primary retail wallet.
     */
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId AND w.walletType = com.manuelorg.cross_pesa.wallet.enums.WalletType.USER_RETAIL")
    Optional<Wallet> findByUserId(@Param("userId") UUID userId);

    /**
     * Checks if a user already owns a retail wallet before creation.
     */
    boolean existsByUserIdAndWalletType(UUID userId, WalletType walletType);

    // --- SYSTEM & OPERATIONAL WALLETS (For Double-Entry Ledger) ---

    /**
     * CRITICAL FOR LEDGER: Fetches system revenue/holding wallets
     * (e.g. SYSTEM_MARKUP for GBP, SYSTEM_ROUTING for KES, SYSTEM_LIQUIDITY for USD).
     */
    Optional<Wallet> findByWalletTypeAndCurrency(WalletType walletType, Currency currency);

    // --- ADMIN DASHBOARD & AUDIT LOOKUPS ---

    /**
     * Paginated fetch for all wallets across the platform.
     */
    Page<Wallet> findAll(Pageable pageable);

    /**
     * Paginated fetch for wallets filtered by status (e.g. FROZEN, SUSPENDED).
     */
    Page<Wallet> findByStatus(WalletStatus status, Pageable pageable);

    /**
     * Paginated fetch filtered by discriminator type (e.g. fetch all USER_RETAIL wallets).
     */
    Page<Wallet> findByWalletType(WalletType walletType, Pageable pageable);
}