package com.manuelorg.cross_pesa.rates;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RateService {
    private final RestClient restClient;
    private final String appId;

    public RateService(
            @Value("${app.exchange-rate.base-url:https://openexchangerates.org/api/}") String baseUrl,
            @Value("${app.exchange-rate.app-id:placeholder_key}") String appId
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.appId = appId;
    }

    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        String source = fromCurrency.toUpperCase();
        String target = toCurrency.toUpperCase();

        try {
            // Fetch the standard payload (implicitly based in USD for free tier)
            OpenExchangeResponse response = restClient.get()
                    .uri("/latest.json?app_id={appId}", appId)
                    .retrieve()
                    .body(OpenExchangeResponse.class);

            if (response == null || response.rates() == null) {
                throw new RuntimeException("Empty or invalid response from Open Exchange Rates");
            }

            // Quick bypass if converting the same currency
            if (source.equals(target)) {
                return BigDecimal.ONE;
            }

            // Get USD value of both source and target currencies
            BigDecimal usdToSourceRate = response.rates().get(source);
            BigDecimal usdToTargetRate = response.rates().get(target);

            if (usdToSourceRate == null || usdToTargetRate == null) {
                throw new IllegalArgumentException(
                        String.format("Unsupported currency pair: %s to %s", source, target)
                );
            }

            // Execute cross-currency math: targetRate / sourceRate
            // Uses 6 decimal places for fine financial precision, rounding up halfway
            return usdToTargetRate.divide(usdToSourceRate, 6, RoundingMode.HALF_UP);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process exchange rate calculation: " + e.getMessage(), e);
        }
    }

    public BigDecimal convert(String fromCurrency, String toCurrency, BigDecimal amount) {
        BigDecimal computedRate = getExchangeRate(fromCurrency, toCurrency);
        return amount.multiply(computedRate).setScale(4, RoundingMode.HALF_UP);
    }
}
