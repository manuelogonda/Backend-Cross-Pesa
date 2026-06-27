package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.wallet.dto.TopUpRequest;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public void topUp(User user, TopUpRequest request) {
        // Find the wallet, or create a new one if it's their first time using this currency
        Wallet wallet = walletRepository.findByUserAndCurrency(user, request.currency())
                .orElseGet(() -> Wallet.builder()
                        .user(user)
                        .currency(request.currency())
                        // The default balance is already set to ZERO in the entity
                        .build());

        // Securely add the top-up amount using BigDecimal math
        wallet.setBalance(wallet.getBalance().add(request.amount()));

        // Save the updated or newly created wallet
        walletRepository.save(wallet);
    }
}
