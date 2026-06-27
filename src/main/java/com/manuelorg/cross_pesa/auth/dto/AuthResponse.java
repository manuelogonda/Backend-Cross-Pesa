package com.manuelorg.cross_pesa.auth.dto;

public record AuthResponse(
        String token,
        String email,
        String firstName,
        String role
) {}
