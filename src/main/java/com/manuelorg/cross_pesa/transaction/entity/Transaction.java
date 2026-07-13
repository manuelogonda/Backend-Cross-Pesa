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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "beneficiary_id", nullable = true, updatable = false)
    private Beneficiary beneficiary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_wallet_id", nullable = false, updatable = false)
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_wallet_id", nullable = true, updatable = false)
    private Wallet destinationWallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_currency", nullable = false, length = 3, updatable = false)
    private Currency sourceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_currency", nullable = false, length = 3, updatable = false)
    private Currency destinationCurrency;

    @Column(name = "source_amount", precision = 18, scale = 4, updatable = false)
    private BigDecimal sourceAmount;

    @Column(name = "destination_amount", precision = 18, scale = 4, updatable = false)
    private BigDecimal destinationAmount;

    @Builder.Default
    @Column(name = "transfer_fee", precision = 18, scale = 4, updatable = false)
    private BigDecimal transferFee = BigDecimal.ZERO;

    @Column(name = "fx_rate_applied", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal fxRateApplied;

    @Column(name = "funding_gateway", length = 50)
    private String fundingGateway;

    @Column(name = "gateway_reference", length = 150, unique = true, nullable = false)
    private String gatewayReference;

    @Column(name = "payout_gateway", length = 50)
    private String payoutGateway;

    @Column(name = "payout_reference", length = 150, unique = true, nullable = false)
    private String payoutReference;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}