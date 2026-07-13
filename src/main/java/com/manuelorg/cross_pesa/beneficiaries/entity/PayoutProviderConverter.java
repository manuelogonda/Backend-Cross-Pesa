package com.manuelorg.cross_pesa.beneficiaries.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.stream.Stream;

@Converter(autoApply = true)
public class PayoutProviderConverter implements AttributeConverter<PayoutProvider, String> {

    @Override
    public String convertToDatabaseColumn(PayoutProvider provider) {
        if (provider == null) return null;
        return provider.getDbValue();
    }

    @Override
    public PayoutProvider convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return Stream.of(PayoutProvider.values())
                .filter(p -> p.getDbValue().equalsIgnoreCase(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payout provider: " + dbValue));
    }
}
