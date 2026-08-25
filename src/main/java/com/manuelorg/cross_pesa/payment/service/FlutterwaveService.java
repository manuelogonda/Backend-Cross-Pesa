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
    private final String webhookSecret;

    public FlutterwaveService(
            @Value("${flutterwave.secret-key}") String secretKey,
            @Value("${flutterwave.base-url}") String baseUrl,
            @Value("${flutterwave.redirect-url}") String redirectUrl,
            @Value("${flutterwave.webhook-secret}") String webhookSecret
    ) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.redirectUrl = redirectUrl;
        this.webhookSecret = webhookSecret;

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
        public record Data(String status, String amount, String currency, String tx_ref, Customer customer) {}
        public record Customer(String email) {}
    }

    /**
     * Authoritative payment details as reported by Flutterwave.
     * These values — never client-supplied ones — must be used to credit wallets.
     */
    public record VerifiedPayment(BigDecimal amount, String currency, String customerEmail, String txRef) {}

    /**
     * Initialises a Flutterwave Standard checkout session.
     * A stable {@code txRef} is generated internally so the caller does not need to supply one.
     * Use {@link #initializePayment(String, String, BigDecimal, String, String)} when you want
     * to supply your own reference (e.g. to link to an existing Transaction record).
     */
    public String initializePayment(String userEmail, String userName, String amount, String currency) {
        String txRef = "CROSSPESA-" + UUID.randomUUID();
        return initializePayment(userEmail, userName, new BigDecimal(amount), currency, txRef);
    }

    /**
     * Initialises a Flutterwave Standard checkout session with a caller-supplied {@code txRef}.
     * The {@code txRef} must be unique and should map to a Transaction / gateway reference in our DB.
     */
    public String initializePayment(String userEmail, String userName, BigDecimal amount, String currency, String txRef) {
        FlutterwaveInitRequest requestPayload = FlutterwaveInitRequest.builder()
                .tx_ref(txRef)
                .amount(amount.toPlainString())
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

        log.info("Sending payment initialisation request to {}/payments for txRef={}", baseUrl, txRef);

        FlutterwaveInitResponse response = restClient.post()
                .uri(baseUrl + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(FlutterwaveInitResponse.class);

        if (response != null && "success".equals(response.getStatus())) {
            log.info("Payment link generated for txRef={}", txRef);
            return response.getData().getLink();
        }

        log.error("Failed to initialise payment link for txRef={}. Response: {}", txRef, response);
        throw new RuntimeException("Failed to initialize payment link");
    }

    /**
     * Validates a Flutterwave webhook signature using constant-time comparison.
     * Flutterwave sends the raw webhook secret as the {@code verif-hash} header value.
     */
    public boolean isValidWebhookSignature(String verifHash) {
        if (verifHash == null || verifHash.isBlank()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                verifHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /**
     * Verifies a transaction with Flutterwave and returns the gateway-reported
     * payment details (amount, currency, payer email) if and only if the
     * transaction is confirmed successful. Returns {@code null} otherwise.
     *
     * Callers MUST credit wallets using the returned values, never values
     * supplied by the client.
     */
    public VerifiedPayment verifyTransaction(String transactionId) {
        log.info("Verifying transaction {} with Flutterwave...", transactionId);

        try {
            FlutterwaveVerifyResponse response = restClient.get()
                    .uri(baseUrl + "/transactions/" + transactionId + "/verify")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(FlutterwaveVerifyResponse.class);

            if (response != null && "success".equals(response.status()) && response.data() != null) {
                FlutterwaveVerifyResponse.Data data = response.data();

                if ("successful".equals(data.status())) {
                    VerifiedPayment verified = new VerifiedPayment(
                            new BigDecimal(data.amount()),
                            data.currency(),
                            data.customer() != null ? data.customer().email() : null,
                            data.tx_ref()
                    );
                    log.info("Transaction {} verified successfully: amount={} currency={}",
                            transactionId, verified.amount(), verified.currency());
                    return verified;
                }

                log.warn("Transaction {} verification failed: status={}", transactionId, data.status());
            }
        } catch (Exception e) {
            log.error("Verification call failed for transaction {}: {}", transactionId, e.getMessage());
        }
        return null;
    }
}