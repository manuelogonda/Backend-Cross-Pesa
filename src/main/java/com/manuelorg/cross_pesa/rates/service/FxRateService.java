package com.manuelorg.cross_pesa.rates.service;

import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.entity.FxRate;
import com.manuelorg.cross_pesa.rates.entity.Provider;
import com.manuelorg.cross_pesa.rates.repository.FxRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxRepository fxRateRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${cross-pesa.fx.oer.app-id}")
    private String oerAppId;

    // Constant markup percentage: 1.5% platform spread fee
    private static final BigDecimal PLATFORM_MARKUP = new BigDecimal("0.0150");

    /**
     * Gets a live valid conversion rate quote.
     */
    @Transactional
    public FxRateResponse getLiveQuote(Currency source, Currency destination) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Check if we have a valid unexpired rate cached in the DB
        return fxRateRepository.findLatestValidRate(source, destination, now)
                .map(FxRateResponse::fromEntity)
                .orElseGet(() -> {
                    // 2. Fallback: Generate a fresh rate quote (Mocking a liquidity API response)
                    FxRate freshRate = fetchFromLiquidityProvider(source, destination);
                    FxRate savedRate = fxRateRepository.save(freshRate);
                    return FxRateResponse.fromEntity(savedRate);
                });
    }

    /**
     * Simulates making an external API call to Flutterwave or Korapay
     * and calculating your commercial client rate.
     */
    private FxRate fetchFromLiquidityProvider(Currency source, Currency destination) {
        // 1. Fetch the live base rate from OER
        BigDecimal midMarketRate = getLiveMidMarketRate(source, destination);

        // 2. Client Rate Math: mid_market_rate * (1 - markup_percentage)
        BigDecimal markupFactor = BigDecimal.ONE.subtract(PLATFORM_MARKUP);
        BigDecimal clientRate = midMarketRate.multiply(markupFactor)
                .setScale(6, RoundingMode.HALF_UP);

        return FxRate.builder()
                .sourceCurrency(source)
                .destinationCurrency(destination)
                .provider(Provider.CONVERA) // Arbitrarily assigning Convera for OER data
                .midMarketRate(midMarketRate)
                .markupPercentage(PLATFORM_MARKUP)
                .clientRate(clientRate)
                .validFrom(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusMinutes(10)) // Quotes expire in 10 minutes
                .build();
    }

    /**
     * Helper to return raw market baseline values for local testing.
     */
    private BigDecimal getLiveMidMarketRate(Currency source, Currency destination) {
        try {
            // OER Endpoint: https://openexchangerates.org/api/latest.json?app_id=YOUR_APP_ID
            String apiUrl = String.format("https://openexchangerates.org/api/latest.json?app_id=%s", oerAppId);

            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> rates = (Map<String, Object>) response.getBody().get("rates");

                // OER Free Tier always returns USD as the base.
                // If the user wants to convert USD to KES, we just grab the KES rate.
                if (source == Currency.USD && rates.containsKey(destination.name())) {
                    return new BigDecimal(rates.get(destination.name()).toString());
                }

                // If the user wants to convert KES to USD, we must invert the rate (1 / KES_RATE)
                if (destination == Currency.USD && rates.containsKey(source.name())) {
                    BigDecimal sourceRateVsUsd = new BigDecimal(rates.get(source.name()).toString());
                    return BigDecimal.ONE.divide(sourceRateVsUsd, 6, RoundingMode.HALF_UP);
                }

                // Cross-currency conversion (e.g., GBP to KES) via USD base
                if (rates.containsKey(source.name()) && rates.containsKey(destination.name())) {
                    BigDecimal sourceRateVsUsd = new BigDecimal(rates.get(source.name()).toString());
                    BigDecimal destRateVsUsd = new BigDecimal(rates.get(destination.name()).toString());
                    // Formula: DestRate / SourceRate
                    return destRateVsUsd.divide(sourceRateVsUsd, 6, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            System.err.println("External FX API failed: " + e.getMessage());
        }

        return getLiveMidMarketRate(source, destination);
    }

}
