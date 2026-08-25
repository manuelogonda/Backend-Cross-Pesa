package com.manuelorg.cross_pesa.beneficiaries.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayoutProvider {
    MPESA("M-PESA"),
    EQUITY_BANK("EQUITY BANK"),
    VISA("VISA"),
    MASTERCARD("MASTERCARD"),
    PAYSTACK("PAYSTACK");

    private final String dbValue;
}