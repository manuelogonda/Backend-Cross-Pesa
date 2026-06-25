package com.manuelorg.cross_pesa.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    // Finds the precise currency wallet belonging to the authenticated user's email
    Optional<Wallet> findByUserEmailAndCurrency(String email, String currency);
}
