package com.manuelorg.cross_pesa.auth.stepup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StepUpVerifyRequest(
        @NotNull(message = "Challenge ID is required")
        UUID challengeId,

        @NotBlank(message = "Step-up code is required")
        String code
) {}
