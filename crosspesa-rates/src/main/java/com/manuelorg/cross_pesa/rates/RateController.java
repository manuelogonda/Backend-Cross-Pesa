package com.manuelorg.cross_pesa.rates;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rates")
@RequiredArgsConstructor
public class RateController {
    private final RateService exchangeRateService;

    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> getConversion(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount
    ) {
        BigDecimal rate = exchangeRateService.getExchangeRate(from, to);
        BigDecimal convertedAmount = exchangeRateService.convert(from, to, amount);

        Map<String, Object> response = new HashMap<>();
        response.put("from", from.toUpperCase());
        response.put("to", to.toUpperCase());
        response.put("amount", amount);
        response.put("appliedRate", rate);
        response.put("convertedAmount", convertedAmount);

        return ResponseEntity.ok(response);
    }
}
