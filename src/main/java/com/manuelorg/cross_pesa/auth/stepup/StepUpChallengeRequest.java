package com.manuelorg.cross_pesa.auth.stepup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StepUpChallengeRequest(
        @NotNull(message = "Step-up action is required")
        StepUpAction action,

        @NotBlank(message = "Step-up context is required")
        String context
) {}
