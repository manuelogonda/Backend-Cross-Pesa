package com.manuelorg.cross_pesa.rates;

import java.math.BigDecimal;
import java.util.Map;

public record OpenExchangeResponse(
        String disclaimer,
        String license,
        Long timestamp,
        String base,
        Map<String, BigDecimal> rates
) {

}
