package com.manuelorg.cross_pesa.payment.paystack;

import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbound payout integration with the Paystack Transfers API.
 *
 * Two-step payout pattern:
 * 1. Register (or reuse) a transfer recipient for the saved beneficiary
 *    ({@code POST /transferrecipient}).
 * 2. Initiate the transfer ({@code POST /transfer}) using our internal,
 *    globally-unique {@code payoutReference} as the idempotency reference so a
 *    network retry can never create a second payout for one ledger transaction.
 *
 * Isolated from the Flutterwave top-up integration by design.
 */
@Slf4j
@Service
public class PaystackPayoutService {

    private final RestClient restClient;
    private final String secretKey;

    /** Recipient codes cached per beneficiary id to avoid re-registering. */
    private final Map<java.util.UUID, String> recipientCodeCache = new ConcurrentHashMap<>();

    public PaystackPayoutService(
            @Value("${paystack.secret-key}") String secretKey,
            @Value("${paystack.base-url}") String baseUrl
    ) {
        this.secretKey = secretKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }

    // -------------------------------------------------------------------------
    // API response envelopes
    // -------------------------------------------------------------------------

    public record PaystackEnvelope(String status, String message, Object data) {}

    public record TransferInitiation(String reference, String transferCode, String status) {}

    // -------------------------------------------------------------------------
    // Step 1: Create / fetch transfer recipient
    // -------------------------------------------------------------------------

    /**
     * Returns the Paystack recipient code for the given beneficiary, registering
     * it with {@code POST /transferrecipient} on first use and caching afterwards.
     */
    public synchronized String createOrGetRecipient(Beneficiary beneficiary) {
        String cached = recipientCodeCache.get(beneficiary.getId());
        if (cached != null) {
            return cached;
        }
        if (beneficiary.getPaystackRecipientCode() != null && !beneficiary.getPaystackRecipientCode().isBlank()) {
            recipientCodeCache.put(beneficiary.getId(), beneficiary.getPaystackRecipientCode());
            return beneficiary.getPaystackRecipientCode();
        }

        String type = resolveTransferType(beneficiary);
        Map<String, Object> payload = Map.of(
                "type", type,
                "name", beneficiary.getFirstName() + " " + beneficiary.getLastName(),
                "account_number", beneficiary.getAccountNumber(),
                "currency", beneficiary.getAccountCurrency().name()
        );

        log.atInfo()
                .addKeyValue("event", "paystack.recipient.create")
                .addKeyValue("beneficiaryId", beneficiary.getId())
                .addKeyValue("transferType", type)
                .log("Registering Paystack transfer recipient");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/transferrecipient")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String recipientCode = extractRecipientCode(response);

        recipientCodeCache.put(beneficiary.getId(), recipientCode);
        return recipientCode;
    }

    /**
     * Persists the recipient code on the beneficiary after the surrounding DB
     * transaction commits, so subsequent payouts skip registration entirely.
     * Called from an after-commit hook — never inside an active transaction that
     * holds wallet locks.
     */
    public void cacheRecipient(java.util.UUID beneficiaryId, String recipientCode) {
        recipientCodeCache.put(beneficiaryId, recipientCode);
    }

    private String extractRecipientCode(Map<String, Object> response) {
        if (response == null || !Boolean.TRUE.equals(response.get("status"))) {
            log.error("Paystack recipient creation failed: {}",
                    response != null ? response.get("message") : "null response");
            throw new IllegalStateException("Paystack recipient registration failed");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null || data.get("recipient_code") == null) {
            throw new IllegalStateException("Paystack recipient response missing recipient_code");
        }
        return (String) data.get("recipient_code");
    }

    private String resolveTransferType(Beneficiary beneficiary) {
        return beneficiary.getPayoutMethod() == PayoutMethod.MOBILE_MONEY
                ? "mobile_money"
                : "nuban";
    }

    // -------------------------------------------------------------------------
    // Step 2: Initiate outbound transfer
    // -------------------------------------------------------------------------

    /**
     * Initiates the outbound transfer. The {@code reference} MUST be our internal
     * unique payoutReference — Paystack rejects duplicate references, giving us
     * gateway-side idempotency on top of the DB unique index.
     */
    public TransferInitiation initiateTransfer(
            String reference, String recipientCode, BigDecimal amount,
            String currency, String reason
    ) {
        Map<String, Object> payload = Map.of(
                "source", "balance",
                "amount", amount.movePointRight(2).toBigIntegerExact().toString(), // paystack expects minor units
                "recipient", recipientCode,
                "reason", reason,
                "reference", reference,
                "currency", currency
        );

        log.atInfo()
                .addKeyValue("event", "paystack.transfer.initiate")
                .addKeyValue("reference", reference)
                .addKeyValue("amountMinorUnits", amount.movePointRight(2).toBigIntegerExact())
                .addKeyValue("currency", currency)
                .log("Initiating Paystack outbound transfer");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/transfer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        return extractTransfer(response, reference);
    }

    private TransferInitiation extractTransfer(Map<String, Object> response, String reference) {
        if (response == null || !Boolean.TRUE.equals(response.get("status"))) {
            log.error("Paystack transfer initiation failed for reference {}: {}",
                    reference, response != null ? response.get("message") : "null response");
            throw new IllegalStateException("Paystack transfer initiation failed");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return new TransferInitiation(
                (String) data.getOrDefault("reference", reference),
                (String) data.get("transfer_code"),
                (String) data.get("status")
        );
    }

    // -------------------------------------------------------------------------
    // Status verification (settlement worker reconciliation)
    // -------------------------------------------------------------------------

    /** Raw provider status string, e.g. success / failed / reversed / processing / otp. */
    public String verifyTransferStatus(String reference) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/transfer/verify/" + reference)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("status"))) {
                log.warn("Paystack verify returned non-success envelope for reference {}", reference);
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            return data != null ? (String) data.get("status") : null;
        } catch (Exception e) {
            log.error("Paystack verify call failed for reference {}: {}", reference, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Webhook signature validation
    // -------------------------------------------------------------------------

    /**
     * Validates the {@code x-paystack-signature} header: HMAC-SHA512 of the raw
     * request body keyed with our secret key, hex-encoded. Constant-time compare.
     */
    public boolean isValidWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || rawBody == null) {
            return false;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(
                    HexFormat.of().formatHex(expected).getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Webhook signature validation error: {}", e.getMessage());
            return false;
        }
    }
}
