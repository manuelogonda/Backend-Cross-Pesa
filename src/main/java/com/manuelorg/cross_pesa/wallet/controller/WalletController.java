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
     *
     * Security: the credited amount, currency and payer identity are taken
     * exclusively from Flutterwave's verify API — client-supplied values are
     * never trusted. Protected against replay via gateway reference idempotency
     * in WalletService.
     *
     * NOTE: In production, wallet credits should be driven by a signature-validated
     * webhook; this redirect-driven flow is a fallback for sandbox testing.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyTopUp(
            @RequestParam String transactionId,
            @AuthenticationPrincipal User currentUser
    ) {
        // 1. Only accept numeric Flutterwave transaction IDs (also prevents URI path injection)
        if (transactionId == null || !transactionId.matches("\\d{1,20}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid transaction ID."));
        }

        // 2. Verify with Flutterwave — the response is the sole source of truth
        FlutterwaveService.VerifiedPayment verified =
                flutterwaveService.verifyTransaction(transactionId);
        if (verified == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed."));
        }

        // 3. The verified payment must belong to the authenticated user
        if (verified.customerEmail() == null
                || !verified.customerEmail().equalsIgnoreCase(currentUser.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment does not belong to this account."));
        }

        // 4. Parse the gateway-reported currency (never the client's)
        Currency targetCurrency;
        try {
            targetCurrency = Currency.valueOf(verified.currency().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported currency code: " + verified.currency()));
        }

        // 5. Deposit the gateway-reported amount via the Double-Entry Engine,
        //    keyed by the FLW transaction ID for idempotency
        walletService.addFunds(
                currentUser.getId(),
                targetCurrency,
                verified.amount(),
                "FLW-" + transactionId
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Wallet funded successfully! Ledger updated."
        ));
    }
}