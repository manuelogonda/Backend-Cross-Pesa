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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    //cron job worker

    List<Transaction> findByStatusIn(List<TransactionStatus> statuses, Pageable batchLimit);

    // --- IDEMPOTENCY & LOOKUPS ---

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    Optional<Transaction> findByGatewayReference(String gatewayReference);

    // --- FRAUD ENGINE ---

    /**
     * Velocity Check for Fraud Engine to count how many transactions
     * a user has attempted within a recent time window.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sender.id = :userId AND t.createdAt >= :since")
    long countRecentTransactionsByUser(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    // --- USER-FACING STATEMENTS ---

    Page<Transaction> findBySenderId(UUID senderId, Pageable pageable);

    // --- GLOBAL ADMIN PAGINATION ---

    Page<Transaction> findAll(Pageable pageable);

    Page<Transaction> findAllByStatus(TransactionStatus status, Pageable pageable);

    // --- ADMIN DASHBOARD METRICS ---

    long countByStatus(TransactionStatus status);

    long countByCreatedAtAfter(OffsetDateTime date);

    long countByStatusIn(List<TransactionStatus> statuses);

    /**
     * Calculates Gross Revenue (Total Fees collected).
     * Parameterized the status to strictly enforce the TransactionStatus Enum type.
     */
    @Query("SELECT COALESCE(SUM(t.totalFee), 0) FROM Transaction t WHERE t.createdAt >= :since AND t.status = :status")
    BigDecimal sumPlatformFeesSince(
            @Param("since") OffsetDateTime since,
            @Param("status") TransactionStatus status
    );

    /**
     * Calculates Net Revenue (Platform Margin only, excluding external routing costs).
     * Parameterized the status to strictly enforce the TransactionStatus Enum type.
     */
    @Query("SELECT COALESCE(SUM(t.markupFee), 0) FROM Transaction t WHERE t.createdAt >= :since AND t.status = :status")
    BigDecimal sumNetMarkupRevenueSince(
            @Param("since") OffsetDateTime since,
            @Param("status") TransactionStatus status
    );


    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    @Query("SELECT SUM(t.totalFee) FROM Transaction t WHERE t.createdAt >= :date")
    BigDecimal sumTotalFeeByCreatedAtAfter(@Param("date") OffsetDateTime date);
}
