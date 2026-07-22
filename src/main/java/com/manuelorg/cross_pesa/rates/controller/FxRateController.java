package com.manuelorg.cross_pesa.rates.controller;

import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx-rates")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    /**
     * GET /api/v1/fx-rates/quote?source=GBP&destination=KES
     * Fetches or calculates a single live, unexpired FX quote.
     */
    @GetMapping("/quote")
    public ResponseEntity<FxRateResponse> getRateQuote(
            @RequestParam String source,
            @RequestParam String destination
    ) {
        FxRateResponse response = fxRateService.getLiveQuote(
                source.toUpperCase().trim(),
                destination.toUpperCase().trim()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/fx-rates
     * Retrieves a paginated history of cached/stored exchange rates for administrative tracking.
     *
     * Example: /api/v1/fx-rates?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<FxRateResponse>> getAllRates(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<FxRateResponse> rates = fxRateService.getRateHistory(pageable);
        return ResponseEntity.ok(rates);
    }
}