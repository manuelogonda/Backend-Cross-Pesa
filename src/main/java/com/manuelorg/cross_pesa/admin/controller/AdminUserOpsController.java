package com.manuelorg.cross_pesa.admin.controller;

import com.manuelorg.cross_pesa.admin.service.AdminUserOpsService;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserOpsController {

    private final AdminUserOpsService userOpsService;

    @GetMapping("/{userId}/wallet")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<WalletResponse> getUserWallet(@PathVariable UUID userId) {
        return ResponseEntity.ok(userOpsService.getUserRetailWallet(userId));
    }

    @GetMapping("/{userId}/ledger")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Page<LedgerEntryResponse>> getUserLedger(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(userOpsService.getUserLedger(userId, pageable));
    }

    @PostMapping("/{userId}/wallet/status")
    @PreAuthorize("hasRole('ADMIN')") // Only compliance can freeze accounts
    public ResponseEntity<Map<String, Object>> changeWalletStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal User adminUser
    ) {
        WalletStatus newStatus = WalletStatus.valueOf(payload.get("status").toUpperCase());
        WalletResponse updatedWallet = userOpsService.updateWalletStatus(userId, newStatus, adminUser.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "Wallet status updated successfully",
                "wallet", updatedWallet
        ));
    }
}
