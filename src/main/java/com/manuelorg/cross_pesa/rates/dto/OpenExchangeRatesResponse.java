package com.manuelorg.cross_pesa.rates.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OpenExchangeRatesResponse(
        String disclaimer,
        String license,
        Long timestamp,
        String base,
        Map<String, BigDecimal> rates
) {}
