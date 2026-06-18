package com.manuelorg.cross_pesa.features.transfer;

import com.manuelorg.cross_pesa.features.transfer.enums.TransactionStatus;
import com.manuelorg.cross_pesa.features.wallet.Wallet;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_sender"))
    private Wallet senderWallet;

    // Nullable for external remittances (e.g., direct utility bills or external cash-outs)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_wallet_id", foreignKey = @ForeignKey(name = "fk_transactions_receiver"))
    private Wallet receiverWallet;

    @Column(name = "recipient_type", nullable = false, length = 30)
    private String recipientType;

    @Column(name = "recipient_reference", nullable = false, length = 255)
    private String recipientReference;

    @Column(name = "source_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "destination_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal destinationAmount;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "mpesa_receipt_no", unique = true, length = 50)
    private String mpesaReceiptNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
