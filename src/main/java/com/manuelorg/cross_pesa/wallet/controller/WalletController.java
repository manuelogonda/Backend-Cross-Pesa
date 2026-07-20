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
    @PostMapping("/topup")
    public ResponseEntity<Map<String, String>> initiateTopUp(
            @AuthenticationPrincipal User currentUser, // Spring Security automatically injects the logged-in user
            @Valid @RequestBody TopUpRequest request
    ) {
        System.out.println(" REQUEST REACHED THE CONTROLLER! User: " + currentUser.getEmail());
        // 1. Call Flutterwave to generate the secure payment link
        String paymentLink = flutterwaveService.initializePayment(
                currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                request.amount().toPlainString(),
                String.valueOf(request.currency())
        );

        // 2. Return the link to the React frontend
        // React will receive this and execute: window.location.href = response.data.paymentLink;
        return ResponseEntity.ok(Map.of(
                "message", "Payment link generated successfully",
                "paymentLink", paymentLink
        ));
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

    @PostMapping("/verify")
    public ResponseEntity<?> verifyTopUp(
            @RequestParam String transactionId,
            @RequestParam String amount,
            @RequestParam String currency,
            @AuthenticationPrincipal User currentUser) { // Make sure you are injecting the logged-in user!

        // 1. Ask Flutterwave if this transaction is real and matches our data
        boolean isValid = flutterwaveService.verifyTransaction(transactionId, amount, currency);

        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed or amount mismatch."));
        }

        // 2. IT IS REAL! Now we actually add the funds to PostgreSQL
        // We convert the String currency to your Enum, and the String amount to a BigDecimal
        walletService.addFunds(
                currentUser.getId(),
                Currency.valueOf(currency.toUpperCase()),
                new BigDecimal(amount)
        );

        // 3. Send the success response back to React
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Wallet funded successfully!"
        ));
    }
}