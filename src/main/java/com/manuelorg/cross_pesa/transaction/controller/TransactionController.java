package com.manuelorg.cross_pesa.transaction.controller;


import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.service.TransactionService;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * POST /api/v1/transactions/send
     * Executes a cross-border transfer to an external beneficiary.
     */
    @PostMapping("/send")
    public ResponseEntity<TransactionResponse.SendMoneyResponse> sendMoney(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TransactionRequest.SendMoneyRequest request
    ) {
        TransactionResponse.SendMoneyResponse response = transactionService.processSendMoney(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/transactions/p2p — REMOVED.
     * P2P wallet-to-wallet transfers are deprecated; outbound funds now flow
     * exclusively through the saved-beneficiary Flutterwave payout workflow
     * (POST /api/v1/transactions/send).
     */

    /**
     * GET /api/v1/transactions
     * Retrieves the authenticated user's paginated transaction history statement.
     * Example: GET /api/v1/transactions?page=0&size=15
     */
    @GetMapping
    public ResponseEntity<Page<TransactionResponse.SendMoneyResponse>> getUserTransactionHistory(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TransactionResponse.SendMoneyResponse> history = transactionService.getUserTransactionHistory(currentUser.getId(), pageable);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/v1/transactions/{id}
     * Fetches single transaction details by ID for receipts/audit.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse.SendMoneyResponse> getTransactionById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        TransactionResponse.SendMoneyResponse transaction = transactionService.getTransactionById(currentUser.getId(), id);
        return ResponseEntity.ok(transaction);
    }
}
