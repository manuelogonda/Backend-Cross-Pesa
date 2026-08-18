package com.manuelorg.cross_pesa.wallet.entity;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true) // Allow nulls for System Wallets
    @JoinColumn(name = "user_id", nullable = true)      // Match the SQL schema
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 30, updatable = false)
    private WalletType walletType = WalletType.USER_RETAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Builder.Default
    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "locked_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal lockedBalance = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status = WalletStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ==========================================
    // DOMAIN LOGIC HELPERS
    // ==========================================

    /**
     * Calculates the true spendable balance.
     * Prevents users from spending funds that are tied up in pending transactions.
     * Never returns a negative value.
     */
    public BigDecimal getAvailableBalance() {
        BigDecimal available = this.balance.subtract(
                this.lockedBalance != null ? this.lockedBalance : BigDecimal.ZERO);
        return available.max(BigDecimal.ZERO);
    }

    /**
     * Throws if the wallet is not ACTIVE.
     * Call this before any balance mutation.
     */
    public void ensureActive() {
        if (this.status != WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet " + this.id + " is not ACTIVE (status=" + this.status + ").");
        }
    }
}