package com.manuelorg.cross_pesa.rates.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.stream.Stream;

@Converter(autoApply = true)
public class ProviderConverter implements AttributeConverter<Provider, String> {

    @Override
    public String convertToDatabaseColumn(Provider provider) {
        if (provider == null) {
            return null;
        }
        return provider.getDbValue();
    }

    @Override
    public Provider convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return Stream.of(Provider.values())
                .filter(p -> p.getDbValue().equals(dbValue))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}
