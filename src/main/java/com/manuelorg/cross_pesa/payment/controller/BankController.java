package com.manuelorg.cross_pesa.payment.controller;

import com.manuelorg.cross_pesa.payment.service.FlutterwaveBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payouts/banks")
public class BankController {

    private final FlutterwaveBankService bankService;

    public BankController(FlutterwaveBankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping("/{countryCode}")
    public ResponseEntity<List<FlutterwaveBankService.BankDto>> getBanks(@PathVariable String countryCode) {
        List<FlutterwaveBankService.BankDto> banks = bankService.getBanksForCountry(countryCode.toUpperCase());
        return ResponseEntity.ok(banks);
    }
}
