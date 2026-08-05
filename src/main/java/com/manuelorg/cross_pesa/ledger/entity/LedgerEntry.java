package com.manuelorg.cross_pesa.ledger.entity;

import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // --- RELATIONS ---

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    // --- ENTRY CLASSIFICATION & CURRENCY ---

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_class", nullable = false, length = 50, updatable = false)
    private EntryClass entryClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    // --- FINANCIAL AMOUNTS ---

    @Builder.Default
    @Column(name = "debit", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "credit", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal credit = BigDecimal.ZERO;

    /**
     * Set dynamically by PostgreSQL trigger BEFORE INSERT.
     * We mark this updatable = false to respect the immutability trigger.
     */
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.Generated
    private BigDecimal balanceAfter = BigDecimal.ZERO;

    @Column(name = "description", nullable = false, length = 255, updatable = false)
    private String description;

    // --- PRICING ENGINE AUDIT TRAIL ---

    @Column(name = "routing_pair", length = 10, updatable = false)
    private String routingPair;

    @Column(name = "markup_tiers_applied", length = 100, updatable = false)
    private String markupTiersApplied;

    @Column(name = "usd_baseline_amount", precision = 18, scale = 4, updatable = false)
    private BigDecimal usdBaselineAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
