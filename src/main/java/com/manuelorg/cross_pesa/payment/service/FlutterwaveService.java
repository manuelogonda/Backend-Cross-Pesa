package com.manuelorg.cross_pesa.payment.service;

import com.manuelorg.cross_pesa.payment.dto.FlutterwaveInitRequest;
import com.manuelorg.cross_pesa.payment.dto.FlutterwaveInitResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

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

        // 10-second timeout
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

        System.out.println(" Sending Payment Request to " + baseUrl + "/payments ...");

        FlutterwaveInitResponse response = restClient.post()
                .uri(baseUrl + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey) // v3 uses the Secret Key directly!
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(FlutterwaveInitResponse.class);

        System.out.println(" Payment Link Generated!");

        if (response != null && "success".equals(response.getStatus())) {
            return response.getData().getLink();
        }

        throw new RuntimeException("Failed to initialize payment link");
    }

    /**
     * Verifies the final status of a transaction with Flutterwave
     */
    public boolean verifyTransaction(String transactionId, String expectedAmount, String expectedCurrency) {
        System.out.println("⏳ Verifying transaction " + transactionId + " with Flutterwave...");

        try {
            FlutterwaveVerifyResponse response = restClient.get()
                    .uri(baseUrl + "/transactions/" + transactionId + "/verify")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(FlutterwaveVerifyResponse.class);

            if (response != null && "success".equals(response.status())) {
                FlutterwaveVerifyResponse.Data data = response.data();

                // CRITICAL SECURITY CHECK: Ensure they paid the correct amount and currency!
                boolean isSuccessful = "successful".equals(data.status());
                boolean isCorrectAmount = Double.parseDouble(expectedAmount) <= Double.parseDouble(data.amount());
                boolean isCorrectCurrency = expectedCurrency.equals(data.currency());

                System.out.println("🔍 --- DEBUGGING VERIFICATION MISMATCH ---");
                System.out.println("Status Match: " + isSuccessful + " (Expected: successful | Actual: " + data.status() + ")");
                System.out.println("Amount Match: " + isCorrectAmount + " (Expected: " + expectedAmount + " | Actual: " + data.amount() + ")");
                System.out.println("Currency Match: " + isCorrectCurrency + " (Expected: " + expectedCurrency + " | Actual: " + data.currency() + ")");
                System.out.println("-------------------------------------------");

                if (isSuccessful && isCorrectAmount && isCorrectCurrency) {
                    System.out.println("Transaction verified successfully!");
                    return true;
                } else {
                    System.out.println("Transaction verified, but data mismatch (Amount/Currency/Status).");
                }
            }
        } catch (Exception e) {
            System.err.println("Verification failed: " + e.getMessage());
        }
        return false;
    }
}