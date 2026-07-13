package com.manuelorg.cross_pesa.transaction.controller;


import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.service.TransactionService;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * POST /api/v1/transactions/send
     * Executes a cross-border transfer to an external beneficiary.
     * Subject to payout fees and stricter AML compliance.
     */
    @PostMapping("/send")
    public ResponseEntity<TransactionResponse.SendMoneyResponse> sendMoney(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TransactionRequest.SendMoneyRequest request
    ) {
        // Delegate to service
        TransactionResponse.SendMoneyResponse response = transactionService.processSendMoney(currentUser, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/transactions/exchange
     * Executes an internal transfer between the user's own multi-currency wallets.
     * Only subject to the platform's FX spread (no payout fees).
     */
    @PostMapping("/exchange")
    public ResponseEntity<TransactionResponse.ExchangeResponse> exchangeFunds(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TransactionRequest.ExchangeFundsRequest request
    ) {
        // Delegate to service
        TransactionResponse.ExchangeResponse response = transactionService.processInternalExchange(currentUser, request);
        return ResponseEntity.ok(response);
    }
}
