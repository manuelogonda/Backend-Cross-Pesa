package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

    private final TransactionRepository transactionRepository;
    private final SystemWalletEngine systemWalletEngine; // Contains your cross-border ledger logic
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSingleTransaction(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (tx.getCreatedAt().plusMinutes(1).isAfter(OffsetDateTime.now())) {
            return;
        }

        boolean externalGatewaySuccess = simulateExternalProviderPayout(tx);

        if (externalGatewaySuccess) {
            // 1. Flip status to COMPLETED
            tx.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(tx);

            // 2. CRITICAL: Now that the payout is confirmed, fire the ledger settlement
            // so the user debit finalizes and SYSTEM_MARKUP / SYSTEM_ROUTING get their credit!
            systemWalletEngine.executeCrossBorderSettlement(
                    tx,
                    tx.getSourceWallet(),
                    tx.getGrossAmount(),
                    tx.getMarkupFee(),
                    tx.getRoutingFee(),
                    tx.getDestinationCurrency(),
                    tx.getDestinationAmount(),
                    // Pass routing attributes stored on your transaction entity
                    "USD-" + tx.getSourceCurrency(), // Example routing pair format
                    "DEFAULT_TIER",
                    tx.getUsdNormalizationRate()
            );

            log.info("Transaction ID: {} successfully reconciled, marked COMPLETED, and system wallets credited.", tx.getId());
        } else {
            log.warn("Transaction ID: {} payout still processing at external bank.", tx.getId());
        }

        entityManager.flush();
        entityManager.clear();
    }

    private boolean simulateExternalProviderPayout(Transaction tx) {
        return tx.getCreatedAt().plusMinutes(1).isBefore(OffsetDateTime.now());
    }
}
