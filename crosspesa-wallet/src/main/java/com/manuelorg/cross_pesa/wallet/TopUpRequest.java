package com.manuelorg.cross_pesa.wallet;

import java.math.BigDecimal;

public record TopUpRequest(
        BigDecimal amount,
        String currency
) {}
