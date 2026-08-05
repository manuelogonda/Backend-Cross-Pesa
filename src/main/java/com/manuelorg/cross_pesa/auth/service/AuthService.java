package com.manuelorg.cross_pesa.auth.service;

import com.manuelorg.cross_pesa.auth.dto.AuthResponse;
import com.manuelorg.cross_pesa.auth.dto.LoginRequest;
import com.manuelorg.cross_pesa.auth.dto.RegisterRequest;
import com.manuelorg.cross_pesa.auth.entity.AuthProvider;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.config.JwtService;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    public AuthResponse register(RegisterRequest request) {
        // RECORD syntax: request.email()
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (repository.existsByPhoneNumber(request.phoneNumber())) {
            throw new IllegalArgumentException("Phone number is already registered");
        }

        var user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .kycStatus(KycStatus.PENDING)
                .build();

        User savedUser = repository.save(user);

        var accessToken = jwtService.generateAccessToken(savedUser);
        var refreshToken = jwtService.generateRefreshToken(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .firstName(user.getFirstName()) // CLASS syntax: user.getFirstName()
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // CLASS syntax: request.getEmail()
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        var accessToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .firstName(user.getFirstName())
                .build();
    }
}