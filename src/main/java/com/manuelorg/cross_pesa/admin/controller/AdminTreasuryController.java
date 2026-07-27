package com.manuelorg.cross_pesa.admin.controller;

import com.manuelorg.cross_pesa.admin.dto.TreasuryRebalanceRequest;
import com.manuelorg.cross_pesa.admin.service.AdminTreasuryService;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/treasury")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTreasuryController {

    private final AdminTreasuryService treasuryService;

    @GetMapping("/wallets")
    public ResponseEntity<Page<WalletResponse>> getSystemWallets(
            @RequestParam WalletType type,
            Pageable pageable
    ) {
        return ResponseEntity.ok(treasuryService.getSystemWallets(type, pageable));
    }

    @PostMapping("/rebalance")
    public ResponseEntity<Map<String, String>> rebalancePools(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody TreasuryRebalanceRequest request
    ) {
        treasuryService.executeRebalance(admin, request);
        return ResponseEntity.ok(Map.of("message", "Treasury rebalance executed and logged successfully."));
    }
}
