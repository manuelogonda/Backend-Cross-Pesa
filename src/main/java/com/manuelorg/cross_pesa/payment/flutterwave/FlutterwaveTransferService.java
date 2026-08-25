package com.manuelorg.cross_pesa.payment.flutterwave;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbound payout integration with the Flutterwave Transfers API (v3).
 *
 * <p>Two-step payout pattern:
 * <ol>
 *   <li>Resolve and validate a transfer recipient for the saved beneficiary
 *       (network code + account details). Flutterwave's v3 API embeds the
 *       recipient inline in the transfer request, so the "recipient" here is a
 *       validated, cached descriptor rather than a server-side object.</li>
 *   <li>Initiate the transfer ({@code POST /transfers}) using our internal,
 *       globally-unique {@code payoutReference} as the idempotency reference so
 *       a network retry can never create a second payout for one ledger
 *       transaction.</li>
 * </ol>
 *
 * <p>Flutterwave supports bank and mobile-money payouts across KES, NGN, GHS,
 * UGX, TZS and more — both in sandbox and production — making it the single
 * payment provider for Cross-Pesa.
 */
@Slf4j
@Service
public class FlutterwaveTransferService {

    private final RestClient restClient;
    private final String secretKey;
    private final String webhookUrl;

    /** Validated recipient descriptors cached per beneficiary id. */
    private final Map<UUID, Recipient> recipientCache = new ConcurrentHashMap<>();

    public FlutterwaveTransferService(
            @Value("${flutterwave.secret-key}") String secretKey,
            @Value("${flutterwave.base-url}") String baseUrl,
            @Value("${flutterwave.transfer-webhook-url:}") String webhookUrl
    ) {
        this.secretKey = secretKey;
        this.webhookUrl = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl;

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

    public record TransferInitiation(String reference, Long transferId, String status) {}

    /**
     * Validated destination descriptor for one beneficiary.
     * {@code networkCode} is the Flutterwave bank / mobile-money network code
     * (e.g. {@code 058} for GTB, {@code MPS} for M-PESA Kenya).
     */
    public record Recipient(UUID beneficiaryId, String fullName, String networkCode,
                            String accountNumber, String currency, PayoutMethod payoutMethod) {}

    // -------------------------------------------------------------------------
    // Step 1: Resolve / validate transfer recipient
    // -------------------------------------------------------------------------

    /**
     * Returns the validated recipient descriptor for the given beneficiary.
     * Validates that all routing fields required by Flutterwave are present and
     * caches the result so subsequent payouts skip re-validation.
     */
    public synchronized Recipient createOrGetRecipient(Beneficiary beneficiary) {
        Recipient cached = recipientCache.get(beneficiary.getId());
        if (cached != null) {
            return cached;
        }

        if (beneficiary.getBankCode() == null || beneficiary.getBankCode().isBlank()) {
            throw new IllegalStateException(
                    "Beneficiary " + beneficiary.getId() + " has no bank/network code; "
                            + "Flutterwave requires account_bank to initiate a transfer");
        }
        if (beneficiary.getAccountNumber() == null || beneficiary.getAccountNumber().isBlank()) {
            throw new IllegalStateException(
                    "Beneficiary " + beneficiary.getId() + " has no account number");
        }

        Recipient recipient = new Recipient(
                beneficiary.getId(),
                (beneficiary.getFirstName() + " " + beneficiary.getLastName()).trim(),
                resolveNetworkCode(beneficiary),
                beneficiary.getAccountNumber(),
                beneficiary.getAccountCurrency().name(),
                beneficiary.getPayoutMethod()
        );

        log.atInfo()
                .addKeyValue("event", "flutterwave.recipient.resolved")
                .addKeyValue("beneficiaryId", beneficiary.getId())
                .addKeyValue("payoutMethod", recipient.payoutMethod())
                .log("Resolved Flutterwave transfer recipient");

        recipientCache.put(beneficiary.getId(), recipient);
        return recipient;
    }

    /**
     * Persists nothing on the gateway side; kept for symmetry with the payout
     * flow so callers can warm the cache after a beneficiary update commits.
     */
    public void cacheRecipient(java.util.UUID beneficiaryId, Recipient recipient) {
        recipientCache.put(beneficiaryId, recipient);
    }

    /** Drops any cached recipient, e.g. when a beneficiary's routing details change. */
    public void invalidateRecipient(java.util.UUID beneficiaryId) {
        recipientCache.remove(beneficiaryId);
    }

    private String resolveNetworkCode(Beneficiary beneficiary) {
        if (beneficiary.getPayoutMethod() == PayoutMethod.MOBILE_MONEY) {
            return mapMobileMoneyNetwork(beneficiary.getBankCode(),
                    beneficiary.getAccountCurrency().name());
        }
        return beneficiary.getBankCode().trim();
    }

    /**
     * Maps internal mobile-money provider names to Flutterwave network codes.
     * Unknown codes are passed through unchanged so new corridors work without
     * a redeploy.
     */
    private String mapMobileMoneyNetwork(String bankCode, String currency) {
        return switch ((bankCode == null ? "" : bankCode).trim().toUpperCase()) {
            case "MPESA" -> switch (currency) {
                case "KES" -> "MPS";
                case "TZS" -> "TIGO"; // fallback handled by pass-through below if wrong
                default -> "MPS";
            };
            case "AIRTEL" -> "ATL";
            case "MTN" -> "MTN";
            default -> bankCode.trim();
        };
    }

    // -------------------------------------------------------------------------
    // Step 2: Initiate outbound transfer
    // -------------------------------------------------------------------------

    /**
     * Initiates the outbound transfer via {@code POST /transfers}.
     *
     * @param reference MUST be our internal unique payoutReference — Flutterwave
     *                  rejects duplicate references, giving us gateway-side
     *                  idempotency on top of the DB unique index
     */
    public TransferInitiation initiateTransfer(
            String reference, Recipient recipient, BigDecimal amount,
            String currency, String narration
    ) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("account_bank", recipient.networkCode());
        payload.put("account_number", recipient.accountNumber());
        payload.put("amount", amount.toPlainString());
        payload.put("narration", narration != null ? narration : "Cross-Pesa payout");
        payload.put("currency", currency);
        payload.put("reference", reference);
        if (webhookUrl != null) {
            payload.put("callback_url", webhookUrl);
        }

        log.atInfo()
                .addKeyValue("event", "flutterwave.transfer.initiate")
                .addKeyValue("reference", reference)
                .addKeyValue("amount", amount)
                .addKeyValue("currency", currency)
                .log("Initiating Flutterwave outbound transfer");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        return extractTransfer(response, reference);
    }

    private TransferInitiation extractTransfer(Map<String, Object> response, String reference) {
        if (response == null || !"success".equals(response.get("status"))) {
            log.atError()
                    .addKeyValue("event", "flutterwave.transfer.initiate_failed")
                    .addKeyValue("reference", reference)
                    .log("Flutterwave transfer initiation failed: {}",
                            response != null ? response.get("message") : "null response");
            throw new IllegalStateException("Flutterwave transfer initiation failed");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            throw new IllegalStateException("Flutterwave transfer response missing data");
        }
        Long transferId = data.get("id") instanceof Number n ? n.longValue() : null;
        return new TransferInitiation(
                (String) data.getOrDefault("reference", reference),
                transferId,
                (String) data.get("status")
        );
    }

    // -------------------------------------------------------------------------
    // Status verification (settlement worker reconciliation)
    // -------------------------------------------------------------------------

    /** Raw provider status string, e.g. SUCCESSFUL / FAILED / PENDING / CANCELLED. */
    public String verifyTransferStatus(String reference) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/transfers").queryParam("reference", reference).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"success".equals(response.get("status"))) {
                log.warn("Flutterwave verify returned non-success envelope for reference {}", reference);
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return null;
            }
            return (String) data.getFirst().get("status");
        } catch (Exception e) {
            log.error("Flutterwave verify call failed for reference {}: {}", reference, e.getMessage());
            return null;
        }
    }
}
