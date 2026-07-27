package com.manuelorg.cross_pesa.config;

import com.manuelorg.cross_pesa.auth.entity.AuthProvider;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    @Override
    @Transactional
    public void run(String... args) {
        String adminEmail = "admin@crosspesa.com";

        if (!userRepository.existsByEmail(adminEmail)) {
            log.info("🚀 Seeding initial System Admin: {}", adminEmail);

            User admin = User.builder()
                    .firstName("System")
                    .lastName("Admin")
                    .email(adminEmail)
                    .phoneNumber("+254700000000")
                    .password(passwordEncoder.encode("Admin123!"))
                    .role(Role.ADMIN)
                    .authProvider(AuthProvider.LOCAL)
                    .kycStatus(KycStatus.APPROVED)
                    .build();

            User savedAdmin = userRepository.save(admin);

            // Automatically provision operational administrative wallet
            walletService.createWallet(savedAdmin, Currency.KES);

            log.info("✅ System Admin initialized successfully with KES wallet.");
        } else {
            log.info("ℹ️ System Admin account already present. Skipping seeding.");
        }
    }
}