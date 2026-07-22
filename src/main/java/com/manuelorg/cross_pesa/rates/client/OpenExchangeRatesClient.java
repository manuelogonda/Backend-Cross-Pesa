package com.manuelorg.cross_pesa.rates.client;

import com.manuelorg.cross_pesa.rates.dto.OpenExchangeRatesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenExchangeRatesClient {

    private final RestClient restClient;
    private final String appId;

    // REMOVED RestClient.Builder from the parameters!
    public OpenExchangeRatesClient(
            @Value("${cross-pesa.fx.oer.app-id}") String appId,
            @Value("${cross-pesa.fx.oer.base-url:https://openexchangerates.org/api}") String baseUrl) {

        // Use the static factory method RestClient.builder() instead
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.appId = appId;
    }

    /**
     * Fetches the latest JSON file from the Open Exchange Rates Free Tier.
     * Base currency is always strictly USD.
     */
    public OpenExchangeRatesResponse getLatestRates() {
        try {
            log.info("Calling OpenExchangeRates API for latest USD cross-rates...");

            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest.json")
                            .queryParam("app_id", appId)
                            // Optionally request specific symbols to save bandwidth:
                            // .queryParam("symbols", "GBP,KES,EUR,CNY,JPY,CAD,AUD,PKR,AED,SAR,SEK")
                            .build())
                    .retrieve()
                    .body(OpenExchangeRatesResponse.class);

        } catch (RestClientException e) {
            log.error("Failed to fetch rates from Open Exchange Rates API: {}", e.getMessage());
            throw new IllegalStateException("FX Liquidity Provider is currently unavailable. Please try again later.", e);
        }
    }
}
