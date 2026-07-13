package com.manuelorg.cross_pesa.rates.entity;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fx_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_currency", nullable = false, length = 3)
    private Currency sourceCurrency;

    @Column(name = "provider", nullable = false, length = 100)
    private Provider provider; // Automatically uses the ProviderConverter

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_currency", nullable = false, length = 3)
    private Currency destinationCurrency;

    @Column(name = "mid_market_rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal midMarketRate;

    @Builder.Default
    @Column(name = "markup_percentage", nullable = false, precision = 6, scale = 4)
    private BigDecimal markupPercentage = BigDecimal.ZERO;

    @Column(name = "client_rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal clientRate;

    @Builder.Default
    @Column(name = "valid_from")
    private OffsetDateTime validFrom = OffsetDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
