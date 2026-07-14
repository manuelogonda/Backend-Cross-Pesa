package com.manuelorg.cross_pesa.beneficiaries.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryResponse;
import com.manuelorg.cross_pesa.beneficiaries.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    /**
     * GET /api/v1/beneficiaries
     * Lists all saved payout contacts for the logged-in user (Paginated).
     */
    @GetMapping
    public ResponseEntity<Page<BeneficiaryResponse>> getBeneficiaries(
            @AuthenticationPrincipal User currentUser,
            Pageable pageable // Spring automatically handles ?page=0&size=20
    ) {
        Page<BeneficiaryResponse> beneficiaries = beneficiaryService.getUserBeneficiaries(currentUser.getId(), pageable);
        return ResponseEntity.ok(beneficiaries);
    }

    /**
     * POST /api/v1/beneficiaries
     * Adds a new beneficiary to the user's address book.
     */
    @PostMapping
    public ResponseEntity<BeneficiaryResponse> addBeneficiary(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody BeneficiaryRequest request
    ) {
        BeneficiaryResponse response = beneficiaryService.createBeneficiary(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /api/v1/beneficiaries/{id}
     * Removes a beneficiary from the address book.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeBeneficiary(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        beneficiaryService.deleteBeneficiary(currentUser, id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    /**
     * PUT /api/v1/beneficiaries/{id}
     * Updates an existing beneficiary's details.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> updateBeneficiary(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody BeneficiaryRequest request
    ) {
        BeneficiaryResponse response = beneficiaryService.updateBeneficiary(currentUser, id, request);
        return ResponseEntity.ok(response);
    }
}
