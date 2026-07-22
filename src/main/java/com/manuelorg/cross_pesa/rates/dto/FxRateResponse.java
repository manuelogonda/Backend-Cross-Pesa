package com.manuelorg.cross_pesa.rates.dto;

import com.manuelorg.cross_pesa.rates.entity.FxRate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FxRateResponse(
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal exchangeRate,
        OffsetDateTime expiresAt
) {
    public static FxRateResponse fromEntity(FxRate fxRate) {
        return new FxRateResponse(
                fxRate.getSourceCurrency(),
                fxRate.getDestinationCurrency(),
                fxRate.getRate(),
                fxRate.getExpiresAt()
        );
    }
}
