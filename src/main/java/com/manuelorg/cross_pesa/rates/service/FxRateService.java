package com.manuelorg.cross_pesa.rates.service;

import com.manuelorg.cross_pesa.rates.client.OpenExchangeRatesClient;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.dto.OpenExchangeRatesResponse;
import com.manuelorg.cross_pesa.rates.entity.FxRate;
import com.manuelorg.cross_pesa.rates.repository.FxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxRepository fxRateRepository;
    private final OpenExchangeRatesClient openExchangeRatesClient;

    /**
     * Gets a live quote for any currency pair.
     * If the rate is missing or expired in the database, it calculates the cross-rate.
     */
    @Transactional
    public FxRateResponse getLiveQuote(String sourceCurrency, String destinationCurrency) {
        if (sourceCurrency == null || sourceCurrency.isBlank() || destinationCurrency == null || destinationCurrency.isBlank()) {
            throw new IllegalArgumentException("Source and destination currencies cannot be null or blank");
        }

        String source = sourceCurrency.trim().toUpperCase();
        String destination = destinationCurrency.trim().toUpperCase();

        if (source.equals(destination)) {
            return new FxRateResponse(source, destination, BigDecimal.ONE, OffsetDateTime.now().plusYears(1));
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Check database for an active, unexpired rate
        FxRate activeRate = fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                source, destination, now
        ).orElseGet(() -> fetchAndCalculateCrossRate(source, destination, now));

        return FxRateResponse.fromEntity(activeRate);
    }

    /**
     * Handles the Open Exchange Rates Free Tier Cross-Math limitation.
     */
    private FxRate fetchAndCalculateCrossRate(String sourceCurrency, String destinationCurrency, OffsetDateTime now) {
        log.info("Cache miss. Fetching OpenExchangeRates to calculate {} -> {}", sourceCurrency, destinationCurrency);

        // Fetch the raw JSON map from Open Exchange Rates (Base is strictly USD)
        OpenExchangeRatesResponse response = openExchangeRatesClient.getLatestRates();
        if (response == null || response.rates() == null) {
            throw new IllegalStateException("Received empty exchange rates response from provider");
        }

        // Open Exchange Rates free tier omits "USD": 1.0 from the map, so handle it explicitly
        BigDecimal sourceRateFromUsd = "USD".equals(sourceCurrency)
                ? BigDecimal.ONE
                : response.rates().get(sourceCurrency);

        BigDecimal destRateFromUsd = "USD".equals(destinationCurrency)
                ? BigDecimal.ONE
                : response.rates().get(destinationCurrency);

        if (sourceRateFromUsd == null) {
            throw new IllegalArgumentException("Unsupported or missing source currency rate for: " + sourceCurrency);
        }
        if (destRateFromUsd == null) {
            throw new IllegalArgumentException("Unsupported or missing destination currency rate for: " + destinationCurrency);
        }

        // --- CROSS RATE MATH ---
        // To find GBP -> KES, you divide (USD -> KES) by (USD -> GBP)
        BigDecimal calculatedRate = destRateFromUsd.divide(sourceRateFromUsd, 6, RoundingMode.HALF_UP);

        // Save the rate to the database with a Time-To-Live (TTL)
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
    @Transactional(readOnly = true)
    public Page<FxRateResponse> getRateHistory(Pageable pageable) {
        return fxRateRepository.findAll(pageable)
                .map(FxRateResponse::fromEntity);
    }
}
