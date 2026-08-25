package com.manuelorg.cross_pesa.beneficiaries.entity;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "beneficiaries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_beneficiary_routing",
                        columnNames = {"user_id", "payout_provider", "account_number"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", nullable = false, length = 50)
    private BeneficiaryType beneficiaryType = BeneficiaryType.INDIVIDUAL;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "city", length = 50)
    private String city;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 50)
    private PayoutMethod payoutMethod = PayoutMethod.BANK_TRANSFER;

    @Column(name = "payout_provider", nullable = false, length = 50)
    private PayoutProvider payoutProvider; // Automatically managed by PayoutProviderConverter

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "account_currency", nullable = false, length = 3)
    private Currency accountCurrency = Currency.KES;

    /**
     * Bank or mobile-money network code of the payout provider (e.g. Paystack
     * bank_code). Required by the gateway when registering a transfer recipient.
     */
    @Column(name = "bank_code", length = 20)
    private String bankCode;

    /**
     * Paystack transfer recipient code (RCP_xxx) created for this beneficiary.
     * Cached so the recipient is registered once with Paystack and reused for
     * every payout — never persisted in logs.
     */
    @Column(name = "paystack_recipient_code", length = 100)
    private String paystackRecipientCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}