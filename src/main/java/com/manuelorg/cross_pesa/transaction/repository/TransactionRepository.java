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

    // Checks if we've already processed this exact transaction attempt
    boolean existsByIdempotencyKey(UUID idempotencyKey);
    Optional<Transaction> findByGatewayReference(String gatewayReference);
    // Velocity Check: Count transactions for a user since a specific time
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sender.id = :userId AND t.createdAt >= :since")
    long countRecentTransactionsByUser(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);
    
    //pagination
    Page<Transaction> findAll(Pageable pageable);

    Page<Transaction> findAllByStatus(TransactionStatus status, Pageable pageable);

    // --- ADMIN DASHBOARD QUERIES ---
    // 2. Metrics: Count by Status
    long countByStatus(TransactionStatus status);

    // 3. Metrics: Count transactions created after a certain date (e.g., today)
    long countByCreatedAtAfter(OffsetDateTime date);

    // 4. Metrics: Sum total fees collected today (Revenue)
    @Query("SELECT COALESCE(SUM(t.transferFee), 0) FROM Transaction t WHERE t.createdAt >= :since AND t.status = 'COMPLETED'")
    BigDecimal sumPlatformFeesSince(@Param("since") OffsetDateTime since);
}
