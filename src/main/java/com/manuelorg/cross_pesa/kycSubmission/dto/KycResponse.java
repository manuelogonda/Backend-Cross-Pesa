package com.manuelorg.cross_pesa.kycSubmission.dto;

import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class KycResponse {
    private UUID id;
    private String documentType;
    private String documentCountry;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Optional fields that might only be populated for Admins
    private String idImageUrl;
    private String selfieImageUrl;
    private String userEmail;

    public static KycResponse fromEntity(KycSubmission kyc) {
        return KycResponse.builder()
                .id(kyc.getId())
                .documentType(kyc.getDocumentType())
                .documentCountry(kyc.getDocumentCountry())
                .status(kyc.getStatus())
                .rejectionReason(kyc.getRejectionReason())
                .createdAt(kyc.getCreatedAt())
                .updatedAt(kyc.getUpdatedAt())
                .idImageUrl(kyc.getIdImageUrl())
                .selfieImageUrl(kyc.getSelfieImageUrl())
                .userEmail(kyc.getUser().getEmail()) // Assuming User entity has getEmail()
                .build();
    }
}
