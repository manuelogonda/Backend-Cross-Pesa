package com.manuelorg.cross_pesa.admin.service;

import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserOpsService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * READ: View a customer's retail wallet balance.
     */
    @Transactional(readOnly = true)
    public WalletResponse getUserRetailWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("No retail wallet found for User ID: " + userId));
        return WalletResponse.fromEntity(wallet);
    }

    /**
     * READ: Fetch the exact ledger statement for a customer dispute.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getUserLedger(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(LedgerEntryResponse::fromEntity);
    }

    /**
     * WRITE: Update customer wallet status (Freeze/Suspend).
     */
    @Transactional
    public WalletResponse updateWalletStatus(UUID userId, WalletStatus newStatus, String adminEmail) {
        Wallet wallet = walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found"));

        wallet.setStatus(newStatus);
        walletRepository.save(wallet);

        log.warn("RISK OPS: User {} wallet changed to {} by Admin {}", userId, newStatus, adminEmail);
        return WalletResponse.fromEntity(wallet);
    }
}
