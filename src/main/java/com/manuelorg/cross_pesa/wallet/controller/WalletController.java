package com.manuelorg.cross_pesa.wallet.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.dto.CreateWalletRequest;
import com.manuelorg.cross_pesa.wallet.dto.TopUpRequest;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * GET /api/v1/wallets
     * Fetches all wallets for the currently logged-in user.
     * @AuthenticationPrincipal automatically injects the User object from the JWT context.
     */
    @GetMapping
    public ResponseEntity<List<WalletResponse>> getUserWallets(
            @AuthenticationPrincipal User currentUser
    ) {
        List<WalletResponse> wallets = walletService.getUserWallets(currentUser.getId());
        return ResponseEntity.ok(wallets);
    }

    /**
     * POST /api/v1/wallets/top-up
     * Mock endpoint to simulate adding funds.
     */
    @PostMapping("/top-up")
    public ResponseEntity<WalletResponse> topUpWallet(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TopUpRequest request
    ) {
        WalletResponse response = walletService.mockTopUp(
                currentUser.getId(),
                request.currency(),
                request.amount()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/wallets
     * Creates a new wallet for the currently logged-in user.
     */
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        WalletResponse response = walletService.createWallet(currentUser, request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}