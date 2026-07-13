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
    private final UserRepository userRepository;

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


    @Transactional
    public void updateUserStatus(UUID userId, AdminUserDto.UpdateStatusRequest request, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Don't let admins accidentally suspend themselves or other admins
        if (user.getRole().name().equals("ADMIN")) {
            throw new SecurityException("Cannot modify another Admin's status.");
        }

        user.setStatus(request.status());
        userRepository.save(user);

//        log.info("Admin {} changed User {} status to {}. Reason: {}", adminEmail, userId, request.status(), request.reason());
        // Note: You could fire an Email Notification event here telling the user their account was suspended!
    }

    @Transactional
    public void updateUserKyc(UUID userId, AdminUserDto.UpdateKycRequest request, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setKycStatus(request.kycStatus());
        if (request.kycLevel() != null) {
            user.setKycLevel(request.kycLevel());
        }

        userRepository.save(user);
        log.info("Admin {} updated KYC for User {} to Status: {}, Level: {}. Notes: {}",
                adminEmail, userId, request.kycStatus(), request.kycLevel(), request.adminNotes());
    }

    private AdminUserDto.AdminUserResponse mapToResponse(User user) {
        return new AdminUserDto.AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getIdType(),
                user.getIdNumber(),
                user.getStatus(),
                user.getKycStatus(),
                user.getKycLevel(),
                user.getCreatedAt() // Assuming your base entity has this
        );
    }
}
