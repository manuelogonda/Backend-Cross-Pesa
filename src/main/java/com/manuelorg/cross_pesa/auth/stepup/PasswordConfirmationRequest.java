package com.manuelorg.cross_pesa.auth.stepup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordConfirmationRequest(
        @NotNull(message = "Confirmation action is required")
        StepUpAction action,

        @NotBlank(message = "Confirmation context is required")
        String context,

        @NotBlank(message = "Password is required")
        String password
) {}
