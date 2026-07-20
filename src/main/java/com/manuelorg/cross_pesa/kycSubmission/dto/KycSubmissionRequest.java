package com.manuelorg.cross_pesa.kycSubmission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KycSubmissionRequest {
    @NotBlank(message = "Smile Job ID is required")
    private String smileJobId;

    @NotBlank(message = "Document Type is required")
    private String documentType; // e.g., "NATIONAL_ID", "PASSPORT"

    @NotBlank(message = "Document Country is required")
    private String documentCountry; // e.g., "KE", "NG"
}
