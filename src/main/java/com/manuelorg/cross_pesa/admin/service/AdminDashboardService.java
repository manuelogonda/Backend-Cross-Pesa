package com.manuelorg.cross_pesa.admin.service;

import com.manuelorg.cross_pesa.admin.dto.AdminTransactionResponse;
import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.admin.dto.DashboardMetricsResponse;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics() {
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);

        long totalTransactionsToday = transactionRepository.countByCreatedAtAfter(startOfDay);

        // Count both PENDING and PROCESSING statuses for an accurate dashboard view
        long pendingTransactions = transactionRepository.countByStatusIn(
                List.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING)
        );

        long flaggedTransactions = transactionRepository.countByStatus(TransactionStatus.FLAGGED);

        long completedTransactionsToday = transactionRepository.countByStatusAndCreatedAtAfter(
                TransactionStatus.COMPLETED, startOfDay
        );

        BigDecimal totalRevenueToday = transactionRepository.sumTotalFeeByCreatedAtAfter(startOfDay);
        if (totalRevenueToday == null) {
            totalRevenueToday = BigDecimal.ZERO;
        }

        BigDecimal netMarkupRevenueToday = transactionRepository.sumNetMarkupRevenueSince(
                startOfDay, TransactionStatus.COMPLETED
        );
        if (netMarkupRevenueToday == null) {
            netMarkupRevenueToday = BigDecimal.ZERO;
        }

        return new DashboardMetricsResponse(
                totalTransactionsToday,
                pendingTransactions,
                flaggedTransactions,
                totalRevenueToday,
                netMarkupRevenueToday,
                completedTransactionsToday
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> getTransactions(String status, Pageable pageable) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            TransactionStatus txStatus = TransactionStatus.valueOf(status.trim().toUpperCase());
            return transactionRepository.findByStatus(txStatus, pageable)
                    .map(AdminTransactionResponse::fromEntity);
        }
        return transactionRepository.findAll(pageable)
                .map(AdminTransactionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDto.AdminUserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(AdminUserDto.AdminUserResponse::fromEntity);
    }
}
