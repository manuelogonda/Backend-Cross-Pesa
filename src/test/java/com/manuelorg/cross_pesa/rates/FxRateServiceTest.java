package com.manuelorg.cross_pesa.rates;

import com.manuelorg.cross_pesa.rates.client.OpenExchangeRatesClient;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.dto.OpenExchangeRatesResponse;
import com.manuelorg.cross_pesa.rates.entity.FxRate;
import com.manuelorg.cross_pesa.rates.repository.FxRepository;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock
    private FxRepository fxRateRepository;

    @Mock
    private OpenExchangeRatesClient openExchangeRatesClient;

    @InjectMocks
    private FxRateService fxRateService;

    @Test
    void getLiveQuote_SameCurrency_ReturnsRateOne() {
        FxRateResponse response = fxRateService.getLiveQuote("KES", "kes");

        assertNotNull(response);
        assertEquals("KES", response.sourceCurrency());
        assertEquals("KES", response.destinationCurrency());
        assertEquals(BigDecimal.ONE, response.exchangeRate());
        assertTrue(response.expiresAt().isAfter(OffsetDateTime.now().plusMonths(6)));
        verifyNoInteractions(fxRateRepository);
        verifyNoInteractions(openExchangeRatesClient);
    }

    @Test
    void getLiveQuote_NullOrBlankCurrencies_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> fxRateService.getLiveQuote(null, "KES"));
        assertThrows(IllegalArgumentException.class, () -> fxRateService.getLiveQuote("USD", null));
        assertThrows(IllegalArgumentException.class, () -> fxRateService.getLiveQuote("", "KES"));
        assertThrows(IllegalArgumentException.class, () -> fxRateService.getLiveQuote("USD", "  "));
    }

    @Test
    void getLiveQuote_CachedUnexpiredRate_ReturnsCachedRate() {
        OffsetDateTime now = OffsetDateTime.now();
        FxRate cachedRate = FxRate.builder()
                .id(UUID.randomUUID())
                .sourceCurrency("GBP")
                .destinationCurrency("KES")
                .rate(new BigDecimal("165.500000"))
                .validFrom(now.minusMinutes(5))
                .expiresAt(now.plusMinutes(10))
                .build();

        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("GBP"), eq("KES"), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(cachedRate));

        FxRateResponse response = fxRateService.getLiveQuote("gbp", "kes");

        assertNotNull(response);
        assertEquals("GBP", response.sourceCurrency());
        assertEquals("KES", response.destinationCurrency());
        assertEquals(new BigDecimal("165.500000"), response.exchangeRate());
        verifyNoInteractions(openExchangeRatesClient);
    }

    @Test
    void getLiveQuote_CacheMiss_CalculatesCrossRateWithScale6AndSavesWith15MinTTL() {
        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("GBP"), eq("KES"), any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        Map<String, BigDecimal> rates = Map.of(
                "GBP", new BigDecimal("0.75"),
                "KES", new BigDecimal("130.00")
        );
        OpenExchangeRatesResponse apiResponse = new OpenExchangeRatesResponse(
                "disclaimer", "license", 1700000000L, "USD", rates
        );
        when(openExchangeRatesClient.getLatestRates()).thenReturn(apiResponse);

        when(fxRateRepository.save(any(FxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FxRateResponse response = fxRateService.getLiveQuote("GBP", "KES");

        assertNotNull(response);
        assertEquals("GBP", response.sourceCurrency());
        assertEquals("KES", response.destinationCurrency());
        // 130.00 / 0.75 = 173.333333 (scale 6, HALF_UP)
        assertEquals(new BigDecimal("173.333333"), response.exchangeRate());

        ArgumentCaptor<FxRate> captor = ArgumentCaptor.forClass(FxRate.class);
        verify(fxRateRepository).save(captor.capture());
        FxRate savedRate = captor.getValue();
        assertEquals("GBP", savedRate.getSourceCurrency());
        assertEquals("KES", savedRate.getDestinationCurrency());
        assertEquals(new BigDecimal("173.333333"), savedRate.getRate());

        // Verify TTL is approx 15 minutes
        long ttlMinutes = Duration.between(savedRate.getValidFrom(), savedRate.getExpiresAt()).toMinutes();
        assertEquals(15, ttlMinutes);
    }

    @Test
    void getLiveQuote_CacheMiss_USDSource_CalculatesCorrectly() {
        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("USD"), eq("KES"), any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        Map<String, BigDecimal> rates = Map.of("KES", new BigDecimal("130.50"));
        OpenExchangeRatesResponse apiResponse = new OpenExchangeRatesResponse(
                "disclaimer", "license", 1700000000L, "USD", rates
        );
        when(openExchangeRatesClient.getLatestRates()).thenReturn(apiResponse);
        when(fxRateRepository.save(any(FxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FxRateResponse response = fxRateService.getLiveQuote("USD", "KES");

        assertEquals(new BigDecimal("130.500000"), response.exchangeRate());
    }

    @Test
    void getLiveQuote_CacheMiss_USDDestination_CalculatesCorrectly() {
        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("KES"), eq("USD"), any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        Map<String, BigDecimal> rates = Map.of("KES", new BigDecimal("130.00"));
        OpenExchangeRatesResponse apiResponse = new OpenExchangeRatesResponse(
                "disclaimer", "license", 1700000000L, "USD", rates
        );
        when(openExchangeRatesClient.getLatestRates()).thenReturn(apiResponse);
        when(fxRateRepository.save(any(FxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FxRateResponse response = fxRateService.getLiveQuote("KES", "USD");

        // 1 / 130.00 = 0.007692
        assertEquals(new BigDecimal("0.007692"), response.exchangeRate());
    }

    @Test
    void getLiveQuote_CacheMiss_MissingCurrencyInRates_ThrowsIllegalArgumentException() {
        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("XYZ"), eq("KES"), any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        Map<String, BigDecimal> rates = Map.of("KES", new BigDecimal("130.00"));
        OpenExchangeRatesResponse apiResponse = new OpenExchangeRatesResponse(
                "disclaimer", "license", 1700000000L, "USD", rates
        );
        when(openExchangeRatesClient.getLatestRates()).thenReturn(apiResponse);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fxRateService.getLiveQuote("XYZ", "KES"));
        assertTrue(ex.getMessage().contains("Unsupported or missing source currency rate for: XYZ"));
    }

    @Test
    void getLiveQuote_CacheMiss_NullApiResponse_ThrowsIllegalStateException() {
        when(fxRateRepository.findFirstBySourceCurrencyAndDestinationCurrencyAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("GBP"), eq("KES"), any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        when(openExchangeRatesClient.getLatestRates()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> fxRateService.getLiveQuote("GBP", "KES"));
    }

    @Test
    void getRateHistory_ReturnsPagedHistory() {
        Pageable pageable = PageRequest.of(0, 10);
        FxRate rate = FxRate.builder()
                .id(UUID.randomUUID())
                .sourceCurrency("USD")
                .destinationCurrency("KES")
                .rate(new BigDecimal("130.000000"))
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .build();

        Page<FxRate> page = new PageImpl<>(List.of(rate));
        when(fxRateRepository.findAll(pageable)).thenReturn(page);

        Page<FxRateResponse> result = fxRateService.getRateHistory(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("USD", result.getContent().getFirst().sourceCurrency());
        assertEquals(new BigDecimal("130.000000"), result.getContent().getFirst().exchangeRate());
    }
}
