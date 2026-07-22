package com.manuelorg.cross_pesa.transaction.repository;

import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Idempotency & Lookups
    boolean existsByIdempotencyKey(UUID idempotencyKey);

    Optional<Transaction> findByGatewayReference(String gatewayReference);

    // Velocity Check for Fraud Engine
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sender.id = :userId AND t.createdAt >= :since")
    long countRecentTransactionsByUser(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    // User-Facing Statements & History
    Page<Transaction> findBySenderId(UUID senderId, Pageable pageable);

    // Global Admin Pagination
    Page<Transaction> findAll(Pageable pageable);

    Page<Transaction> findAllByStatus(TransactionStatus status, Pageable pageable);

    // --- ADMIN DASHBOARD METRICS ---
    long countByStatus(TransactionStatus status);

    long countByCreatedAtAfter(OffsetDateTime date);

    // FIXED: Renamed transferFee -> totalFee to match new entity schema
    @Query("SELECT COALESCE(SUM(t.totalFee), 0) FROM Transaction t WHERE t.createdAt >= :since AND t.status = 'COMPLETED'")
    BigDecimal sumPlatformFeesSince(@Param("since") OffsetDateTime since);

    // Optional: Separate Net Revenue (Platform Margin only, excluding routing fees)
    @Query("SELECT COALESCE(SUM(t.markupFee), 0) FROM Transaction t WHERE t.createdAt >= :since AND t.status = 'COMPLETED'")
    BigDecimal sumNetMarkupRevenueSince(@Param("since") OffsetDateTime since);
}
