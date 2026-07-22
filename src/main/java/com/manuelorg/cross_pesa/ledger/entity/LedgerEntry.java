package com.manuelorg.cross_pesa.ledger.entity;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Column(name = "entry_class", nullable = false, length = 50, updatable = false)
    private String entryClass; // E.g., 'PRINCIPAL_TRANSFER', 'MARKUP_FEE', 'ROUTING_FEE', 'FX_CLEARING'

    @Builder.Default
    @Column(name = "debit", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "credit", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal credit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    /**
     * Calculated and injected by PostgreSQL PL/pgSQL trigger before insert.
     * @Generated informs Hibernate 6 to refresh this value from the database upon insert.
     */
    @org.hibernate.annotations.Generated(event = EventType.INSERT)
    @Column(name = "balance_after", insertable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "description", nullable = false, length = 255, updatable = false)
    private String description;

    // --- PRICING ENGINE AUDIT TRAIL ---

    @Column(name = "routing_pair", length = 10, updatable = false)
    private String routingPair; // E.g., 'GBP_KES'

    @Column(name = "markup_tiers_applied", length = 100, updatable = false)
    private String markupTiersApplied; // E.g., 'TIER_1, TIER_2'

    @Column(name = "usd_baseline_amount", precision = 18, scale = 4, updatable = false)
    private BigDecimal usdBaselineAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Fail-fast Java validation mirroring database constraints before persistence.
     */
    @PrePersist
    protected void validateDebitCreditExclusive() {
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new IllegalStateException("LedgerEntry cannot have both debit and credit greater than zero.");
        }
        if (!hasDebit && !hasCredit) {
            throw new IllegalStateException("LedgerEntry must have either a debit or a credit greater than zero.");
        }
    }
}
