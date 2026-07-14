package com.manuelorg.cross_pesa.auth.admin.service;

import com.manuelorg.cross_pesa.auth.admin.dto.AdminTransactionResponse;
import com.manuelorg.cross_pesa.auth.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.auth.admin.dto.DashboardMetricsResponse;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TransactionRepository transactionRepository;

    /**
     * Fetch paginated list of transactions with rich admin details.
     */
    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> getAllTransactions(TransactionStatus status, Pageable pageable) {
        Page<Transaction> transactions;

        if (status != null) {
            transactions = transactionRepository.findAllByStatus(status, pageable);
        } else {
            transactions = transactionRepository.findAll(pageable);
        }

        return transactions.map(AdminTransactionResponse::fromEntity);
    }

    /**
     * Calculate live platform statistics for the dashboard cards.
     */
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics() {
        // Start of today in UTC
        OffsetDateTime startOfToday = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);

        long txToday = transactionRepository.countByCreatedAtAfter(startOfToday);
        long pending = transactionRepository.countByStatus(TransactionStatus.PENDING);
        long flagged = transactionRepository.countByStatus(TransactionStatus.FLAGGED);
        BigDecimal revenue = transactionRepository.sumPlatformFeesSince(startOfToday);

        return new DashboardMetricsResponse(txToday, pending, flagged, revenue);
    }
}
