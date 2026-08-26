package com.manuelorg.cross_pesa.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class FlutterwaveBankService {

    private final RestClient restClient;
    private final String secretKey;

    public FlutterwaveBankService(
            @Value("${flutterwave.secret-key}") String secretKey,
            @Value("${flutterwave.base-url}") String baseUrl
    ) {
        this.secretKey = secretKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record BankDto(int id, String code, String name) {}

    public record BankResponse(String status, String message, List<BankDto> data) {}

    /**
     * Fetches live supported banks/networks for a given country code (e.g. "KE", "UG", "TZ", "NG").
     */
    public List<BankDto> getBanksForCountry(String countryCode) {
        try {
            BankResponse response = restClient.get()
                    .uri("/banks/{country}", countryCode)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(BankResponse.class);

            if (response != null && "success".equalsIgnoreCase(response.status())) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Failed to fetch banks for country {}: {}", countryCode, e.getMessage());
        }
        return List.of();
    }
}
