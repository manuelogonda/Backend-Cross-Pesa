package com.manuelorg.cross_pesa.ledger.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getWalletStatement(User currentUser, UUID walletId) {
        // 1. Fetch the wallet
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        // 2. Security Check: Does this wallet belong to the logged-in user?
        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You do not have permission to view this ledger.");
        }

        // 3. Fetch and map the ledger entries
        return ledgerEntryRepository.findAllByWalletIdOrderByCreatedAtDesc(walletId)
                .stream()
                .map(LedgerEntryResponse::fromEntity)
                .toList();
    }
}
