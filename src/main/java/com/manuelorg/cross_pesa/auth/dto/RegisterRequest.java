package com.manuelorg.cross_pesa.auth.dto;

public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String password
) {}