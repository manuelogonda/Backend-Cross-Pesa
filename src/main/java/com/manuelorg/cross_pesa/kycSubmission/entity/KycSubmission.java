package com.manuelorg.cross_pesa.kycSubmission.entity;

import com.manuelorg.cross_pesa.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_submissions", indexes = {
        @Index(name = "idx_kyc_smile_job_id", columnList = "smile_job_id"),
        @Index(name = "idx_kyc_user_id", columnList = "user_id"),
        @Index(name = "idx_kyc_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "smile_job_id", unique = true, nullable = false, length = 100)
    private String smileJobId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_country", nullable = false, length = 10)
    private String documentCountry;

    @Column(name = "id_image_url", length = 500)
    private String idImageUrl;

    @Column(name = "selfie_image_url", length = 500)
    private String selfieImageUrl;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
