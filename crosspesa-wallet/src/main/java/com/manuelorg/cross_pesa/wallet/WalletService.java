package com.manuelorg.cross_pesa.wallet;

import com.manuelorg.cross_pesa.user.User;
import com.manuelorg.cross_pesa.user.UserRepository; // Ensure you import your UserRepository
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository; // Needed to bind the user to a new wallet

    @Transactional
    public Wallet topUp(String email, TopUpRequest request) {
        String targetCurrency = request.currency().toUpperCase();

        // Check if the wallet exists, if not, create it on the fly!
        Wallet wallet = walletRepository.findByUserEmailAndCurrency(email, targetCurrency)
                .orElseGet(() -> {
                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

                    return Wallet.builder()
                            .user(user)
                            .currency(targetCurrency)
                            .balance(BigDecimal.ZERO)
                            .status(WalletStatus.ACTIVE)
                            .build();
                });

        // Add the top-up amount
        wallet.setBalance(wallet.getBalance().add(request.amount()));

        return walletRepository.save(wallet);
    }
}
