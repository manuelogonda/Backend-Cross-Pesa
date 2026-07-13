package com.manuelorg.cross_pesa.rates.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Provider {
    CHIPPER_CASH("Chipper Cash"),
    FLUTTERWAVE("Flutter wave"),
    NIUM("Nium"),
    CONVERA("Convera"),
    KORAPAY("Korapay");

    private final String dbValue;
}
