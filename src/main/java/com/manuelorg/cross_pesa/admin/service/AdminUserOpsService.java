package com.manuelorg.cross_pesa.admin.service;

import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserOpsService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;



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
    public WalletResponse updateWalletStatus(UUID userId, WalletStatus newStatus, String reason, String adminEmail) {
        Wallet wallet = walletRepository.findByUserIdAndWalletType(userId, WalletType.USER_RETAIL)
                .orElseThrow(() -> new IllegalArgumentException("Retail wallet not found"));

        wallet.setStatus(newStatus);
        Wallet savedWallet = walletRepository.save(wallet);

        log.warn("RISK OPS: User {} wallet changed to {} by Admin {}. Reason: {}", userId, newStatus, adminEmail, reason);
        return WalletResponse.fromEntity(savedWallet);
    }

    @Transactional
    public void updateUserKyc(UUID userId, AdminUserDto.UpdateKycRequest request, User adminUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // 1. Update user aggregate KYC status and level
        user.setKycStatus(request.kycStatus());
        user.setKycLevel(request.kycLevel());
        userRepository.save(user);

        // 2. Update the user's latest KYC submission record
        List<KycSubmission> submissions = kycSubmissionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (!submissions.isEmpty()) {
            KycSubmission latestKyc = submissions.getFirst();
            latestKyc.setStatus(String.valueOf(request.kycStatus()));
            latestKyc.setRejectionReason(request.adminNotes());
            latestKyc.setReviewedAt(java.time.LocalDateTime.now());
            latestKyc.setReviewedBy(adminUser);
            kycSubmissionRepository.save(latestKyc);
        }

        log.warn("COMPLIANCE OPS: User {} KYC updated to status {} Level {} by Admin {}. Notes: {}",
                userId, request.kycStatus(), request.kycLevel(), adminUser.getEmail(), request.adminNotes());
    }
}
