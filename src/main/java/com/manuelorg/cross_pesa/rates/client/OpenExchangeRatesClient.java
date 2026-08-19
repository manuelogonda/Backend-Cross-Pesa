package com.manuelorg.cross_pesa.rates.client;

import com.manuelorg.cross_pesa.rates.dto.OpenExchangeRatesResponse;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OpenExchangeRatesClient {

    private final RestClient restClient;
    private final String appId;
    private final String supportedSymbols;

    public OpenExchangeRatesClient(
            @Value("${cross-pesa.fx.oer.app-id}") String appId,
            @Value("${cross-pesa.fx.oer.base-url:https://openexchangerates.org/api}") String baseUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.appId = appId;
        this.supportedSymbols = Arrays.stream(Currency.values())
                .map(Enum::name)
                .collect(Collectors.joining(","));
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
                            .queryParam("symbols", supportedSymbols)
                            .build())
                    .retrieve()
                    .body(OpenExchangeRatesResponse.class);

        } catch (RestClientException e) {
            log.error("Failed to fetch rates from Open Exchange Rates API: {}", e.getMessage());
            throw new IllegalStateException("FX Liquidity Provider is currently unavailable. Please try again later.", e);
        }
    }
}
