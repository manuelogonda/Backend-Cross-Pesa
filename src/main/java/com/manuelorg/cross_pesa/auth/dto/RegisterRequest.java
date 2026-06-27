package com.manuelorg.cross_pesa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotBlank String phoneNumber,
        @NotBlank @Size(min = 5, message = "Password must be at least 5 characters") String password
) {}
