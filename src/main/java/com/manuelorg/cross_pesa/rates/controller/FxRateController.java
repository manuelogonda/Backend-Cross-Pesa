package com.manuelorg.cross_pesa.rates.controller;

import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/quote")
    public ResponseEntity<FxRateResponse> getRateQuote(
            @RequestParam Currency source,
            @RequestParam Currency destination
    ) {
        FxRateResponse response = fxRateService.getLiveQuote(source, destination);
        return ResponseEntity.ok(response);
    }
}