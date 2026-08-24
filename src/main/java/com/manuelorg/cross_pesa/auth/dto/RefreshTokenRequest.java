package com.manuelorg.cross_pesa.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token must not be blank")
        String refreshToken
) {}
