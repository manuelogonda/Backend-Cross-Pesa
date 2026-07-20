package com.manuelorg.cross_pesa.kycSubmission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KycActionRequest {
    @NotBlank(message = "Action must be APPROVED or REJECTED")
    private String action; // "APPROVED" or "REJECTED"

    // Required only if action is REJECTED
    private String reason;
}
