package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionSettlementWorker {

    private final TransactionRepository transactionRepository;
    private final TransactionSettlementService settlementService; // Isolated transactional bean

    /**
     * Runs 30 seconds AFTER the previous execution finishes entirely.
     * Prevents multi-threaded overlap and race conditions.
     */
    @Scheduled(fixedDelay = 30000)
    public void processPendingSettlements() {
        // Fetch only a safe batch of 100 at a time to prevent RAM overload
        Pageable batchLimit = PageRequest.of(0, 100);

        List<Transaction> processingTransactions = transactionRepository.findByStatusIn(
                List.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING),
                batchLimit
        );

        if (processingTransactions.isEmpty()) {
            return;
        }

        log.info("Background Worker: Processing batch of {} transactions for reconciliation.", processingTransactions.size());

        for (Transaction tx : processingTransactions) {
            try {
                // Delegate each transaction to an isolated transactional service method
                settlementService.reconcileSingleTransaction(tx.getId());
            } catch (Exception e) {
                log.error("Failed to reconcile transaction ID: {} in background worker", tx.getId(), e);
            }
        }
    }
}
