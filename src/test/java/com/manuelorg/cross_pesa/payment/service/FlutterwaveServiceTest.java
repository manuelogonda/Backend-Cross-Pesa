package com.manuelorg.cross_pesa.payment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlutterwaveServiceTest {

    @Test
    void isValidWebhookSignature_returnsFalseWhenSecretMissing() {
        FlutterwaveService service = new FlutterwaveService(
                "secret-key",
                "https://api.flutterwave.com/v3",
                "http://localhost/redirect",
                ""
        );

        assertFalse(service.isValidWebhookSignature("expected-signature"));
    }

    @Test
    void isValidWebhookSignature_returnsTrueWhenSignatureMatchesSecret() {
        FlutterwaveService service = new FlutterwaveService(
                "secret-key",
                "https://api.flutterwave.com/v3",
                "http://localhost/redirect",
                "shared-secret"
        );

        assertTrue(service.isValidWebhookSignature("shared-secret"));
    }
}
