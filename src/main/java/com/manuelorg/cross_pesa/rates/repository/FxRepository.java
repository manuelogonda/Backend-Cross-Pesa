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

    @Query("SELECT f FROM FxRate f WHERE f.sourceCurrency = :source AND f.destinationCurrency = :dest AND f.expiresAt > :now ORDER BY f.expiresAt DESC LIMIT 1")
    Optional<FxRate> findActiveRate(
            @Param("source") String sourceCurrency,
            @Param("dest") String destinationCurrency,
            @Param("now") OffsetDateTime now
    );
}
