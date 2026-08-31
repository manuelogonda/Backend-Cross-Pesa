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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final WalletService walletService;
    private final com.manuelorg.cross_pesa.wallet.service.CountryCurrencyResolver countryCurrencyResolver;
    private final RefreshTokenService refreshTokenService;


    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // RECORD syntax: request.email()
        if (repository.existsByEmail(request.email())) {
            log.warn("Rejected registration for existing email {}", request.email());
            throw new IllegalArgumentException("Registration failed");
        }
        if (repository.existsByPhoneNumber(request.phoneNumber())) {
            log.warn("Rejected registration for existing phone number {}", request.phoneNumber());
            throw new IllegalArgumentException("Registration failed");
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
                .orElseThrow(() -> {
                    log.warn("Authenticated principal missing from repository for email {}", request.getEmail());
                    return new BadCredentialsException("Authentication failed");
                });

        return buildAuthResponse(user);
    }

    /**
     * Exchanges a valid refresh token for a fresh token pair.
     * The user is re-loaded from the database so suspended/locked/demoted
     * accounts cannot mint new tokens.
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotationResult = refreshTokenService.rotateRefreshToken(refreshToken);
        var user = rotationResult.user();

        return buildAuthResponse(user, rotationResult.refreshToken());
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = refreshTokenService.issueRefreshToken(user);
        return buildAuthResponse(user, refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        var accessToken = jwtService.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .firstName(user.getFirstName())
                .build();
    }

    public AuthResponse issueTokens(User user) {
        return buildAuthResponse(user);
    }

    public AuthResponse issueTokens(User user, String refreshToken) {
        return buildAuthResponse(user, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
    }
}
