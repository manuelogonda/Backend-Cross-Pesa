package com.manuelorg.cross_pesa.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2CodeExchangeRequest(
        @NotBlank(message = "OAuth2 code must not be blank")
        String code
) {}
