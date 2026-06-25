package com.manuelorg.cross_pesa.wallet;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/topup")
    public ResponseEntity<Wallet> topUp(
            @RequestBody TopUpRequest request,
            Principal principal
    ) {
        Wallet updatedWallet = walletService.topUp(principal.getName(), request);
        return ResponseEntity.ok(updatedWallet);
    }
}
