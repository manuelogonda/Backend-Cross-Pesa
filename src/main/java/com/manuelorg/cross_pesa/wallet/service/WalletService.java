package com.manuelorg.cross_pesa.wallet.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.wallet.dto.TopUpRequest;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository; // Added this injection

    @Transactional
    public void topUp(String userEmail, TopUpRequest request) {
        // 1. Fetch a fresh, Hibernate-managed User entity
        User managedUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Proceed with the wallet logic using the managed entity
        Wallet wallet = walletRepository.findByUserAndCurrency(managedUser, request.currency())
                .orElseGet(() -> Wallet.builder()
                        .user(managedUser)
                        .currency(request.currency())
                        .build());

        // 3. Add the funds securely
        wallet.setBalance(wallet.getBalance().add(request.amount()));

        // 4. Save to database
        walletRepository.save(wallet);
    }
}
