package com.manuelorg.cross_pesa.auth.admin.controller;


import com.manuelorg.cross_pesa.auth.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.auth.admin.service.AdminService;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminService adminUserService;


    @GetMapping
    public ResponseEntity<Page<AdminUserDto.AdminUserResponse>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.getAllTransactions());
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody AdminUserDto.UpdateStatusRequest request,
            @AuthenticationPrincipal UserDetails adminDetails) {

        adminUserService.updateUserStatus(userId, request, adminDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/kyc")
    public ResponseEntity<Void> updateUserKyc(
            @PathVariable UUID userId,
            @RequestBody AdminUserDto.UpdateKycRequest request,
            @AuthenticationPrincipal UserDetails adminDetails) {

        adminUserService.updateUserKyc(userId, request, adminDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
