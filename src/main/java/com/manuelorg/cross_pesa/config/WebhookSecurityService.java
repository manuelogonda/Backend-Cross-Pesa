package com.manuelorg.cross_pesa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Shared verification helpers for inbound provider webhooks.
 *
 * Both secrets are sourced from the environment. If a secret is not
 * configured, every request is rejected (secure default) and an error
 * is logged so misconfiguration is obvious.
 */
@Slf4j
@Component
public class WebhookSecurityService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] payoutSecret;
    private final byte[] smileIdCallbackToken;

    public WebhookSecurityService(
            @Value("${webhook.payout-secret:}") String payoutSecret,
            @Value("${smile-id.callback-token:}") String smileIdCallbackToken
    ) {
        this.payoutSecret = payoutSecret == null ? new byte[0] : payoutSecret.getBytes(StandardCharsets.UTF_8);
        this.smileIdCallbackToken = smileIdCallbackToken == null ? new byte[0] : smileIdCallbackToken.getBytes(StandardCharsets.UTF_8);
        if (this.payoutSecret.length == 0) {
            log.error("PAYOUT_WEBHOOK_SECRET is not configured — all payout webhook calls will be rejected");
        }
        if (this.smileIdCallbackToken.length == 0) {
            log.error("SMILE_ID_CALLBACK_TOKEN is not configured — all Smile ID webhook calls will be rejected");
        }
    }

    /**
     * Validates an HMAC-SHA256 hex signature (header {@code X-Webhook-Signature})
     * computed over the exact raw request body.
     */
    public boolean isValidPayoutSignature(String rawBody, String signatureHeader) {
        if (payoutSecret.length == 0 || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(payoutSecret, HMAC_SHA256));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(signatureHeader.trim()),
                    expected
            );
        } catch (Exception e) {
            log.warn("Payout webhook signature validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Smile ID sends its callback token in the {@code X-Callback-Token} header.
     * Validated with a constant-time comparison.
     */
    public boolean isValidSmileIdToken(String callbackToken) {
        if (smileIdCallbackToken.length == 0 || callbackToken == null || callbackToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8),
                smileIdCallbackToken
        );
    }
}
