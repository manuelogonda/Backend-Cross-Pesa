package com.manuelorg.cross_pesa.wallet.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveService;
import com.manuelorg.cross_pesa.wallet.dto.CreateWalletRequest;
import com.manuelorg.cross_pesa.wallet.dto.TopUpRequest;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final FlutterwaveService flutterwaveService;

    /**
     * GET /api/v1/wallets
     * Fetches the single primary retail wallet for the currently logged-in user.
     */
    @GetMapping
    public ResponseEntity<WalletResponse> getUserWallet(
            @AuthenticationPrincipal User currentUser
    ) {
        WalletResponse wallet = walletService.getUserWallet(currentUser.getId());
        return ResponseEntity.ok(wallet);
    }

    /**
     * POST /api/v1/wallets
     * Creates a new primary retail wallet for the currently logged-in user.
     */
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        WalletResponse response = walletService.createWallet(currentUser, request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/wallets/topup
     * Generates a Flutterwave checkout payment link.
     */
    @PostMapping("/topup")
    public ResponseEntity<Map<String, String>> initiateTopUp(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TopUpRequest request
    ) {
        String paymentLink = flutterwaveService.initializePayment(
                currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                request.amount().toPlainString(),
                request.currency().name()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Payment link generated successfully",
                "paymentLink", paymentLink
        ));
    }

    /**
     * POST /api/v1/wallets/verify
     * Verifies the Flutterwave transaction and credits the user's wallet.
     * Protected against replay attacks via gateway reference tracking.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyTopUp(
            @RequestParam String transactionId,
            @RequestParam String amount,
            @RequestParam String currency,
            @AuthenticationPrincipal User currentUser
    ) {
        // 1. Validate Currency Payload
        Currency targetCurrency;
        try {
            targetCurrency = Currency.valueOf(currency.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported currency code: " + currency));
        }

        // 2. Verify with Flutterwave gateway
        boolean isValid = flutterwaveService.verifyTransaction(transactionId, amount, currency);
        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed or amount mismatch."));
        }

        // 3. Deposit funds safely into user's wallet
        walletService.addFunds(
                currentUser.getId(),
                targetCurrency,
                new BigDecimal(amount)
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Wallet funded successfully!"
        ));
    }
}