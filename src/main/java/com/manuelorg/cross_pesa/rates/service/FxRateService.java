package com.manuelorg.cross_pesa.rates.service;

import com.manuelorg.cross_pesa.rates.client.OpenExchangeRatesClient;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.dto.OpenExchangeRatesResponse;
import com.manuelorg.cross_pesa.rates.entity.FxRate;
import com.manuelorg.cross_pesa.rates.repository.FxRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxRepository fxRateRepository;
    private final OpenExchangeRatesClient openExchangeRatesClient; // Your Feign or RestClient

    /**
     * Gets a live quote for any currency pair.
     * If the rate is missing or expired in the database, it calculates the cross-rate.
     */
    public FxRateResponse getLiveQuote(String sourceCurrency, String destinationCurrency) {
        if (sourceCurrency.equals(destinationCurrency)) {
            return new FxRateResponse(sourceCurrency, destinationCurrency, BigDecimal.ONE, OffsetDateTime.now().plusYears(1));
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Check database for an active, unexpired rate
        FxRate activeRate = fxRateRepository.findActiveRate(sourceCurrency, destinationCurrency, now)
                .orElseGet(() -> fetchAndCalculateCrossRate(sourceCurrency, destinationCurrency, now));

        return FxRateResponse.fromEntity(activeRate);
    }

    /**
     * Handles the Open Exchange Rates Free Tier Cross-Math limitation.
     */
    private FxRate fetchAndCalculateCrossRate(String sourceCurrency, String destinationCurrency, OffsetDateTime now) {
        log.info("Cache miss. Fetching OpenExchangeRates to calculate {} -> {}", sourceCurrency, destinationCurrency);

        // Fetch the raw JSON map from Open Exchange Rates (Base is strictly USD)
        // Response format: { "base": "USD", "rates": { "GBP": 0.78, "KES": 130.0 } }
        OpenExchangeRatesResponse response = openExchangeRatesClient.getLatestRates();

        BigDecimal sourceRateFromUsd = response.rates().get(sourceCurrency);
        BigDecimal destRateFromUsd = response.rates().get(destinationCurrency);

        if (sourceRateFromUsd == null || destRateFromUsd == null) {
            throw new IllegalArgumentException("Unsupported currency pair requested.");
        }

        // --- CROSS RATE MATH ---
        // To find GBP -> KES, you divide (USD -> KES) by (USD -> GBP)
        // Example: 130.0 / 0.78 = 166.666667
        BigDecimal calculatedRate = destRateFromUsd.divide(sourceRateFromUsd, 6, RoundingMode.HALF_UP);

        // 2. Save the rate to the database with a Time-To-Live (TTL)
        FxRate newRate = FxRate.builder()
                .sourceCurrency(sourceCurrency)
                .destinationCurrency(destinationCurrency)
                .rate(calculatedRate)
                .validFrom(now)
                .expiresAt(now.plusMinutes(15)) // 15-minute lock prevents spamming the free API
                .build();

        return fxRateRepository.save(newRate);
    }

    /**
     * Fetches a paginated history of exchange rates stored in the database.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<FxRateResponse> getRateHistory(org.springframework.data.domain.Pageable pageable) {
        return fxRateRepository.findAll(pageable)
                .map(FxRateResponse::fromEntity);
    }
}
