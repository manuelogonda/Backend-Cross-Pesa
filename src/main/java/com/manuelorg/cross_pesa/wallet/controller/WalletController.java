package com.manuelorg.cross_pesa.wallet.controller;

import com.manuelorg.cross_pesa.wallet.dto.TopUpRequest;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/top-up")
    public ResponseEntity<String> topUpWallet(
            Principal principal, // Grabs the securely authenticated user's profile
            @Valid @RequestBody TopUpRequest request
    ) {
        // Pass the email string to the service, not the detached object
        walletService.topUp(principal.getName(), request);

        return ResponseEntity.ok("Wallet top-up initiated successfully");
    }
}
