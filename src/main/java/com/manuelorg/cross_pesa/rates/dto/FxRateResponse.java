package com.manuelorg.cross_pesa.rates.dto;

import com.manuelorg.cross_pesa.rates.entity.FxRate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FxRateResponse(
        UUID quoteId,
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal exchangeRate, // This is the clientRate
        OffsetDateTime expiresAt
) {
    public static FxRateResponse fromEntity(FxRate fxRate) {
        return new FxRateResponse(
                fxRate.getId(),
                fxRate.getSourceCurrency().name(),
                fxRate.getDestinationCurrency().name(),
                fxRate.getClientRate(),
                fxRate.getExpiresAt()
        );
    }
}
