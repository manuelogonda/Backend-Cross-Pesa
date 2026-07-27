package com.manuelorg.cross_pesa.payment.service;

import com.manuelorg.cross_pesa.payment.dto.FlutterwaveInitRequest;
import com.manuelorg.cross_pesa.payment.dto.FlutterwaveInitResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class FlutterwaveService {

    private final RestClient restClient;
    private final String secretKey;
    private final String baseUrl;
    private final String redirectUrl;

    public FlutterwaveService(
            @Value("${flutterwave.secret-key}") String secretKey,
            @Value("${flutterwave.base-url}") String baseUrl,
            @Value("${flutterwave.redirect-url}") String redirectUrl
    ) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.redirectUrl = redirectUrl;

        // 10-second timeout to prevent thread blocking if Flutterwave is down
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // Maps the verification response from Flutterwave
    public record FlutterwaveVerifyResponse(String status, String message, Data data) {
        public record Data(String status, String amount, String currency, String tx_ref) {}
    }

    public String initializePayment(String userEmail, String userName, String amount, String currency) {
        String txRef = "CROSSPESA-" + UUID.randomUUID().toString();

        FlutterwaveInitRequest requestPayload = FlutterwaveInitRequest.builder()
                .tx_ref(txRef)
                .amount(amount)
                .currency(currency)
                .redirect_url(redirectUrl)
                .customer(FlutterwaveInitRequest.Customer.builder()
                        .email(userEmail)
                        .name(userName)
                        .build())
                .customizations(FlutterwaveInitRequest.Customizations.builder()
                        .title("CrossPesa Wallet Top-Up")
                        .description("Fund your cross-border wallet")
                        .build())
                .build();

        log.info("Sending Payment Request to {}/payments ...", baseUrl);

        FlutterwaveInitResponse response = restClient.post()
                .uri(baseUrl + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey) // v3 uses the Secret Key directly!
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(FlutterwaveInitResponse.class);

        if (response != null && "success".equals(response.getStatus())) {
            log.info("Payment Link Generated Successfully for TX: {}", txRef);
            return response.getData().getLink();
        }

        log.error("Failed to initialize payment link. Response: {}", response);
        throw new RuntimeException("Failed to initialize payment link");
    }

    /**
     * Verifies the final status of a transaction with Flutterwave.
     * Uses strict BigDecimal comparison to prevent floating-point precision errors.
     */
    public boolean verifyTransaction(String transactionId, String expectedAmount, String expectedCurrency) {
        log.info("⏳ Verifying transaction {} with Flutterwave...", transactionId);

        try {
            FlutterwaveVerifyResponse response = restClient.get()
                    .uri(baseUrl + "/transactions/" + transactionId + "/verify")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(FlutterwaveVerifyResponse.class);

            if (response != null && "success".equals(response.status())) {
                FlutterwaveVerifyResponse.Data data = response.data();

                // 1. Strict BigDecimal Comparison
                BigDecimal expected = new BigDecimal(expectedAmount);
                BigDecimal actual = new BigDecimal(data.amount());

                // CRITICAL SECURITY CHECK: Ensure they paid the correct amount and currency!
                boolean isSuccessful = "successful".equals(data.status());
                boolean isCorrectAmount = expected.compareTo(actual) <= 0; // Ensures actual is >= expected
                boolean isCorrectCurrency = expectedCurrency.equals(data.currency());

                log.debug("🔍 --- DEBUGGING VERIFICATION MISMATCH ---");
                log.debug("Status Match: {} (Expected: successful | Actual: {})", isSuccessful, data.status());
                log.debug("Amount Match: {} (Expected: {} | Actual: {})", isCorrectAmount, expectedAmount, data.amount());
                log.debug("Currency Match: {} (Expected: {} | Actual: {})", isCorrectCurrency, expectedCurrency, data.currency());
                log.debug("-------------------------------------------");

                if (isSuccessful && isCorrectAmount && isCorrectCurrency) {
                    log.info("✅ Transaction {} verified successfully!", transactionId);
                    return true;
                } else {
                    log.warn("⚠️ Transaction {} verified, but data mismatch (Amount/Currency/Status).", transactionId);
                }
            }
        } catch (Exception e) {
            log.error("❌ Verification failed for transaction {}: {}", transactionId, e.getMessage());
        }
        return false;
    }
}