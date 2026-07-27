package com.manuelorg.cross_pesa.transaction.entity;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // --- RELATIONS ---

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private Beneficiary beneficiary; // Ensure you have a Beneficiary entity stub

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_wallet_id", nullable = false)
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_wallet_id")
    private Wallet destinationWallet; // E.g., The SYSTEM_LIQUIDITY pool

    // --- CURRENCIES ---

    @Enumerated(EnumType.STRING)
    @Column(name = "source_currency", nullable = false, length = 3)
    private Currency sourceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_currency", nullable = false, length = 3)
    private Currency destinationCurrency;

    // --- AMOUNTS (Precision 18, Scale 4) ---

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount;

    @Builder.Default
    @Column(name = "markup_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal markupFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "routing_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal routingFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalFee = BigDecimal.ZERO;

    // --- FX AUDIT TRAIL (Precision 18, Scale 6 for extreme accuracy) ---

    @Column(name = "usd_normalization_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal usdNormalizationRate;

    @Column(name = "fx_rate_applied", nullable = false, precision = 18, scale = 6)
    private BigDecimal fxRateApplied;

    @Column(name = "destination_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal destinationAmount;

    // --- GATEWAY TRACING ---

    @Column(name = "funding_gateway", length = 50)
    private String fundingGateway;

    @Column(name = "gateway_reference", length = 150)
    private String gatewayReference;

    @Column(name = "payout_gateway", length = 50)
    private String payoutGateway;

    @Column(name = "payout_reference", length = 150)
    private String payoutReference;

    // --- METADATA ---

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Builder.Default
    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ==========================================
    // MATHEMATICAL INTEGRITY HOOKS
    // ==========================================

    /**
     * Guarantees the Java state perfectly satisfies the PostgreSQL
     * `chk_total_fee_match` and `chk_net_amount_match` constraints before saving.
     */
    @PrePersist
    @PreUpdate
    public void calculateIntegrityFields() {
        if (markupFee == null) markupFee = BigDecimal.ZERO;
        if (routingFee == null) routingFee = BigDecimal.ZERO;
        if (grossAmount == null) grossAmount = BigDecimal.ZERO;

        this.totalFee = this.markupFee.add(this.routingFee);
        this.netAmount = this.grossAmount.subtract(this.totalFee);
    }
}