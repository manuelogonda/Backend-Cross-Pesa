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
import org.springframework.transaction.annotation.Transactional;
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
    private final WalletService walletService;
    private final com.manuelorg.cross_pesa.wallet.service.CountryCurrencyResolver countryCurrencyResolver;
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;


    @Transactional
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

        // Auto-provision the user's primary native wallet (0.00 balance) in the
        // same atomic transaction: explicit currency > country auto-detection > default.
        Currency nativeCurrency = request.currency() != null
                ? request.currency()
                : countryCurrencyResolver.resolve(request.country(), request.phoneNumber());
        walletService.createWallet(savedUser, nativeCurrency);

        return buildAuthResponse(savedUser);
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

        return buildAuthResponse(user);
    }

    /**
     * Exchanges a valid refresh token for a fresh token pair.
     * The user is re-loaded from the database so suspended/locked/demoted
     * accounts cannot mint new tokens.
     */
    public AuthResponse refresh(String refreshToken) {
        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }

        if (!"refresh".equals(jwtService.extractTokenType(refreshToken))) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }

        var userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid or expired refresh token");
        }

        var user = repository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("User no longer exists"));

        if (user.getStatus() != null && user.getStatus() != com.manuelorg.cross_pesa.auth.entity.UserStatus.ACTIVE) {
            throw new org.springframework.security.authentication.DisabledException("Account is not active");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
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