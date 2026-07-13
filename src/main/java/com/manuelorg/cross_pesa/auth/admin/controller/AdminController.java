package com.manuelorg.cross_pesa.auth.admin.controller;

import com.manuelorg.cross_pesa.auth.admin.dto.AdminTransactionResponse;
import com.manuelorg.cross_pesa.auth.admin.dto.DashboardMetricsResponse;
import com.manuelorg.cross_pesa.auth.admin.service.AdminService;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
// Double protection: Method-level security as a backup to the filter chain
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsResponse> getMetrics() {
        return ResponseEntity.ok(adminService.getDashboardMetrics());
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<AdminTransactionResponse>> getPlatformTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(adminService.getAllTransactions(status, pageable));
    }
}
