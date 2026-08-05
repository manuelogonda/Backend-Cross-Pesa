package com.manuelorg.cross_pesa.admin.service;

import com.manuelorg.cross_pesa.admin.dto.TreasuryRebalanceRequest;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTreasuryService {

    private final SystemWalletEngine systemWalletEngine;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /**
     * READ: View all liquidity pools, markup revenue, and routing liabilities.
     */
    @Transactional(readOnly = true)
    public Page<WalletResponse> getSystemWallets(WalletType type, Pageable pageable) {
        if (type == WalletType.USER_RETAIL) {
            throw new IllegalArgumentException("Treasury cannot query retail wallets.");
        }
        return walletRepository.findByWalletType(type, pageable).map(WalletResponse::fromEntity);
    }

    /**
     * WRITE: Executes a manual treasury rebalance (e.g., selling KES to buy USD).
     */
    @Transactional
    public void executeRebalance(User adminUser, TreasuryRebalanceRequest request) {
        log.info("TREASURY ALERT: Admin {} initiating rebalance: -{} {} -> +{} {}",
                adminUser.getEmail(), request.withdrawAmount(), request.sourceCurrency(),
                request.depositAmount(), request.targetCurrency());

        Wallet sourceLiquidity = systemWalletEngine.getSystemWallet(request.sourceCurrency(), WalletType.SYSTEM_LIQUIDITY);
        Wallet targetLiquidity = systemWalletEngine.getSystemWallet(request.targetCurrency(), WalletType.SYSTEM_LIQUIDITY);
        // Create an audit transaction for the rebalance
        Transaction auditTx = Transaction.builder()
                .sender(adminUser)
                .sourceWallet(sourceLiquidity)
                .destinationWallet(targetLiquidity)
                .sourceCurrency(request.sourceCurrency())
                .destinationCurrency(request.targetCurrency())
                .grossAmount(request.withdrawAmount())
                .netAmount(request.withdrawAmount())
                .destinationAmount(request.depositAmount())
                .usdNormalizationRate(BigDecimal.ONE) // Mocked for internal transfer
                .fxRateApplied(BigDecimal.ONE)        // Mocked for internal transfer
                .gatewayReference("TREASURY-REBALANCE")
                .status(TransactionStatus.COMPLETED)
                .idempotencyKey(UUID.randomUUID())
                .build();

        transactionRepository.save(auditTx);

        // Fire the double-entry engine
        systemWalletEngine.executeTreasuryRebalance(
                auditTx,
                request.sourceCurrency(),
                request.withdrawAmount(),
                request.targetCurrency(),
                request.depositAmount(),
                request.notes()
        );
    }
}
