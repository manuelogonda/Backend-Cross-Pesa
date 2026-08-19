package com.manuelorg.cross_pesa.rates;

import com.manuelorg.cross_pesa.rates.controller.FxRateController;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateControllerTest {

    @Mock
    private FxRateService fxRateService;

    @InjectMocks
    private FxRateController fxRateController;

    @Test
    void getRateQuote_NormalizesToUpperCaseAndReturnsOk() {
        FxRateResponse quoteResponse = new FxRateResponse(
                "GBP",
                "KES",
                new BigDecimal("165.500000"),
                OffsetDateTime.now().plusMinutes(15)
        );

        when(fxRateService.getLiveQuote("GBP", "KES")).thenReturn(quoteResponse);

        ResponseEntity<FxRateResponse> response = fxRateController.getRateQuote(" gbp ", " kes ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GBP", response.getBody().sourceCurrency());
        assertEquals("KES", response.getBody().destinationCurrency());
        assertEquals(new BigDecimal("165.500000"), response.getBody().exchangeRate());
        verify(fxRateService).getLiveQuote("GBP", "KES");
    }

    @Test
    void getAllRates_ReturnsPagedHistory() {
        Pageable pageable = PageRequest.of(0, 20);
        FxRateResponse quoteResponse = new FxRateResponse(
                "USD",
                "KES",
                new BigDecimal("130.000000"),
                OffsetDateTime.now().plusMinutes(15)
        );
        Page<FxRateResponse> page = new PageImpl<>(List.of(quoteResponse));

        when(fxRateService.getRateHistory(pageable)).thenReturn(page);

        ResponseEntity<Page<FxRateResponse>> response = fxRateController.getAllRates(pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        verify(fxRateService).getRateHistory(pageable);
    }
}
