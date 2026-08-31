package com.manuelorg.cross_pesa.admin.controller;

import com.manuelorg.cross_pesa.admin.dto.AdminMessageResponse;
import com.manuelorg.cross_pesa.admin.dto.TreasuryRebalanceRequest;
import com.manuelorg.cross_pesa.admin.service.AdminTreasuryService;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.stepup.StepUpAction;
import com.manuelorg.cross_pesa.auth.stepup.StepUpContextFactory;
import com.manuelorg.cross_pesa.auth.stepup.StepUpService;
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

@RestController
@RequestMapping("/api/v1/admin/treasury")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTreasuryController {

    private final AdminTreasuryService treasuryService;
    private final StepUpService stepUpService;

    @GetMapping("/wallets")
    public ResponseEntity<Page<WalletResponse>> getSystemWallets(
            @RequestParam WalletType type,
            Pageable pageable
    ) {
        return ResponseEntity.ok(treasuryService.getSystemWallets(type, pageable));
    }

    @PostMapping("/rebalance")
    public ResponseEntity<AdminMessageResponse> rebalancePools(
            @AuthenticationPrincipal User admin,
            @RequestHeader(name = StepUpService.STEP_UP_TOKEN_HEADER, required = false) String stepUpToken,
            @Valid @RequestBody TreasuryRebalanceRequest request
    ) {
        stepUpService.requireStepUp(
                admin,
                StepUpAction.ADMIN_TREASURY_REBALANCE,
                StepUpContextFactory.forTreasuryRebalance(request),
                stepUpToken
        );
        treasuryService.executeRebalance(admin, request);
        return ResponseEntity.ok(new AdminMessageResponse("Treasury rebalance executed and logged successfully."));
    }
}
