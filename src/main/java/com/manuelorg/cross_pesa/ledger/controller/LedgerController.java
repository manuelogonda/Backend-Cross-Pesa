package com.manuelorg.cross_pesa.ledger.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * GET /api/v1/ledgers/wallets/{walletId}
     * Retrieves the chronological transaction history (statement) for a specific wallet.
     */
    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<List<LedgerEntryResponse>> getWalletLedger(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID walletId
    ) {
        List<LedgerEntryResponse> statement = ledgerService.getWalletStatement(currentUser, walletId);
        return ResponseEntity.ok(statement);
    }
}
