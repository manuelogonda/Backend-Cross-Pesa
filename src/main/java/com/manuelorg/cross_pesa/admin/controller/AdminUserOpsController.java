package com.manuelorg.cross_pesa.admin.controller;

import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.admin.service.AdminUserOpsService;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import jakarta.validation.Valid;
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
@PreAuthorize("hasRole('ADMIN')") 
public class AdminUserOpsController {

    private final AdminUserOpsService userOpsService;

    @GetMapping("/{userId}/wallet")
    public ResponseEntity<WalletResponse> getUserWallet(@PathVariable UUID userId) {
        return ResponseEntity.ok(userOpsService.getUserRetailWallet(userId));
    }

    @GetMapping("/{userId}/ledger")
    public ResponseEntity<Page<LedgerEntryResponse>> getUserLedger(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(userOpsService.getUserLedger(userId, pageable));
    }

    @PostMapping("/{userId}/wallet/status")
    public ResponseEntity<AdminUserDto.AdminWalletStatusResponse> changeWalletStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserDto.UpdateStatusRequest request,
            @AuthenticationPrincipal User adminUser
    ) {
        WalletResponse updatedWallet = userOpsService.updateWalletStatus(
                userId,
                request.status(),
                request.reason(),
                adminUser.getEmail()
        );

        return ResponseEntity.ok(new AdminUserDto.AdminWalletStatusResponse(
                "Wallet status updated successfully",
                updatedWallet
        ));
    }

    @PutMapping("/{userId}/kyc")
    public ResponseEntity<Void> updateKycStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserDto.UpdateKycRequest request,
            @AuthenticationPrincipal User adminUser
    ) {
        userOpsService.updateUserKyc(userId, request, adminUser);
        return ResponseEntity.noContent().build();
    }
}
