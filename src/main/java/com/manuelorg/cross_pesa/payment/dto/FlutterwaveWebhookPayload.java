package com.manuelorg.cross_pesa.payment.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Typed representation of the Flutterwave webhook payload.
 * Only the fields we act on are mapped; unknown fields are ignored.
 *
 * Example payload shape:
 * {
 *   "event": "charge.completed",
 *   "data": {
 *     "id": 12345,
 *     "tx_ref": "CROSSPESA-abc123",
 *     "flw_ref": "FLW-...",
 *     "status": "successful",
 *     "amount": 5000,
 *     "currency": "KES",
 *     "customer": { "email": "user@example.com" }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlutterwaveWebhookPayload(
        String event,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            Long id,
            @JsonProperty("tx_ref") String txRef,
            @JsonProperty("flw_ref") String flwRef,
            String status,
            BigDecimal amount,
            String currency,
            Customer customer
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            String email,
            String name,
            @JsonProperty("phone_number") String phoneNumber
    ) {}
}
