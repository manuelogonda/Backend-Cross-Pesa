package com.manuelorg.cross_pesa.rates.repository;

import com.manuelorg.cross_pesa.rates.entity.FxRate;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FxRepository extends JpaRepository<FxRate, UUID> {

    // Spring Data automatically translates this to SELECT ... ORDER BY expiresAt DESC LIMIT 1 (or equivalent dialect)
    Optional<FxRate> findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
            String sourceCurrency,
            String destinationCurrency,
            OffsetDateTime now
    );
}
