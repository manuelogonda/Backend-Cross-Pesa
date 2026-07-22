package com.manuelorg.cross_pesa.ledger.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * GET /api/v1/ledgers/statement
     * Retrieves the paginated chronological transaction history for the user's wallet.
     * We removed {walletId} because the backend securely derives it from the logged-in user.
     */
    @GetMapping("/statement")
    public ResponseEntity<Page<LedgerEntryResponse>> getWalletLedger(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Enforce a maximum page size to prevent database DoS attacks
        int maxSafeSize = Math.min(size, 100);

        // Convert the raw integers into a Spring Data Pageable object
        PageRequest pageRequest = PageRequest.of(page, maxSafeSize);

        // Fetch the statement
        Page<LedgerEntryResponse> statement = ledgerService.getWalletStatement(currentUser, pageRequest);

        return ResponseEntity.ok(statement);
    }
}
