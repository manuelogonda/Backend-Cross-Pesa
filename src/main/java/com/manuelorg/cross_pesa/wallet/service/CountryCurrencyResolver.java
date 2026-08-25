package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.wallet.enums.Currency;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves a user's default native currency from their auto-detected country
 * (ISO 3166 alpha-2 code) or, as a fallback, from their phone number's
 * international dialling code.
 */
@Component
public class CountryCurrencyResolver {

    private static final Map<String, Currency> BY_COUNTRY = Map.ofEntries(
            Map.entry("KE", Currency.KES),
            Map.entry("US", Currency.USD),
            Map.entry("CN", Currency.CNY),
            Map.entry("JP", Currency.JPY),
            Map.entry("GB", Currency.GBP),
            Map.entry("CA", Currency.CAD),
            Map.entry("AU", Currency.AUD),
            Map.entry("PK", Currency.PKR),
            Map.entry("AE", Currency.AED),
            Map.entry("SA", Currency.SAR),
            Map.entry("DE", Currency.EUR),
            Map.entry("FR", Currency.EUR),
            Map.entry("IT", Currency.EUR),
            Map.entry("ES", Currency.EUR),
            Map.entry("NL", Currency.EUR),
            Map.entry("IE", Currency.EUR),
            Map.entry("SE", Currency.SEK)
    );

    private static final Map<String, Currency> BY_DIAL_CODE = Map.of(
            "+254", Currency.KES,
            "+1", Currency.USD,
            "+86", Currency.CNY,
            "+81", Currency.JPY,
            "+44", Currency.GBP,
            "+61", Currency.AUD,
            "+92", Currency.PKR,
            "+971", Currency.AED,
            "+966", Currency.SAR,
            "+46", Currency.SEK
    );

    private static final Currency DEFAULT_CURRENCY = Currency.KES;

    /**
     * Resolves the default currency from an ISO country code (e.g. "KE"),
     * falling back to phone-number dial-code detection, then the default.
     */
    public Currency resolve(String countryCode, String phoneNumber) {
        if (countryCode != null && !countryCode.isBlank()) {
            Currency currency = BY_COUNTRY.get(countryCode.trim().toUpperCase(Locale.ROOT));
            if (currency != null) {
                return currency;
            }
        }
        if (phoneNumber != null && phoneNumber.startsWith("+")) {
            return BY_DIAL_CODE.entrySet().stream()
                    .filter(entry -> phoneNumber.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(DEFAULT_CURRENCY);
        }
        return DEFAULT_CURRENCY;
    }
}
