package com.manuelorg.cross_pesa.config;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSecurityServiceTest {

    private static final String PAYOUT_SECRET = "payout-secret";
    private static final String SMILE_TOKEN = "smile-token";

    private final WebhookSecurityService service = new WebhookSecurityService(PAYOUT_SECRET, SMILE_TOKEN);

    private String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(PAYOUT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validHmacSignature_IsAccepted() throws Exception {
        String body = "{\"reference\":\"GW-1\",\"status\":\"SUCCESS\"}";
        assertThat(service.isValidPayoutSignature(body, hmac(body))).isTrue();
    }

    @Test
    void tamperedBody_IsRejected() throws Exception {
        String body = "{\"reference\":\"GW-1\",\"status\":\"SUCCESS\"}";
        String signature = hmac(body);
        String tampered = body.replace("SUCCESS", "FAILED");
        assertThat(service.isValidPayoutSignature(tampered, signature)).isFalse();
    }

    @Test
    void missingOrBlankSignature_IsRejected() {
        assertThat(service.isValidPayoutSignature("{}", null)).isFalse();
        assertThat(service.isValidPayoutSignature("{}", "  ")).isFalse();
    }

    @Test
    void malformedHexSignature_IsRejected() {
        assertThat(service.isValidPayoutSignature("{}", "not-hex!")).isFalse();
    }

    @Test
    void wrongSecret_IsRejected() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("other-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String badSig = HexFormat.of().formatHex(mac.doFinal("{}".getBytes(StandardCharsets.UTF_8)));
        assertThat(service.isValidPayoutSignature("{}", badSig)).isFalse();
    }

    @Test
    void smileIdToken_MatchingConstant_IsAccepted() {
        assertThat(service.isValidSmileIdToken(SMILE_TOKEN)).isTrue();
    }

    @Test
    void smileIdToken_WrongOrMissing_IsRejected() {
        assertThat(service.isValidSmileIdToken("wrong")).isFalse();
        assertThat(service.isValidSmileIdToken(null)).isFalse();
        assertThat(service.isValidSmileIdToken("")).isFalse();
    }

    @Test
    void unconfiguredSecrets_RejectEverything() {
        WebhookSecurityService unconfigured = new WebhookSecurityService("", "");
        assertThat(unconfigured.isValidSmileIdToken(SMILE_TOKEN)).isFalse();
        assertThat(unconfigured.isValidPayoutSignature("{}", "00")).isFalse();
    }
}
