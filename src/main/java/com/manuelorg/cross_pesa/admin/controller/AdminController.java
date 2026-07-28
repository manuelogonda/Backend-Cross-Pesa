package com.manuelorg.cross_pesa.admin.controller;

import com.manuelorg.cross_pesa.admin.dto.AdminTransactionResponse;
import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.admin.dto.DashboardMetricsResponse;
import com.manuelorg.cross_pesa.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminDashboardService adminDashboardService; // Inject your metrics/transactions/users service

    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsResponse> getMetrics() {
        return ResponseEntity.ok(adminDashboardService.getMetrics());
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<AdminTransactionResponse>> getTransactions(
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(adminDashboardService.getTransactions(status, pageable));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDto.AdminUserResponse>> getAllUsers(Pageable pageable) {
        return ResponseEntity.ok(adminDashboardService.getUsers(pageable));
    }
}
