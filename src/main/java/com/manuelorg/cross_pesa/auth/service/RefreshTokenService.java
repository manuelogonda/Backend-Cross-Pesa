package com.manuelorg.cross_pesa.auth.service;

import com.manuelorg.cross_pesa.auth.entity.RefreshToken;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.auth.repository.RefreshTokenRepository;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.config.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public String issueRefreshToken(User user) {
        String rawToken = jwtService.generateRefreshToken(user);
        saveIssuedToken(user, rawToken);
        return rawToken;
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawRefreshToken) {
        ParsedRefreshToken parsed = parseAndHash(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(parsed.tokenHash())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        User currentUser = userRepository.findById(stored.getUser().getId())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));

        if (!currentUser.getEmail().equalsIgnoreCase(parsed.subject())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (currentUser.getStatus() != null && currentUser.getStatus() != UserStatus.ACTIVE) {
            revokeToken(stored);
            refreshTokenRepository.revokeAllActiveByUserId(currentUser.getId(), OffsetDateTime.now());
            throw new DisabledException("Account is not active");
        }

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        revokeToken(stored);
        String newRefreshToken = jwtService.generateRefreshToken(currentUser);
        saveIssuedToken(currentUser, newRefreshToken);

        return new RotationResult(currentUser, newRefreshToken);
    }

    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        ParsedRefreshToken parsed = parseAndHash(rawRefreshToken);
        refreshTokenRepository.findByTokenHashForUpdate(parsed.tokenHash())
                .ifPresent(this::revokeToken);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, OffsetDateTime.now());
    }

    private void saveIssuedToken(User user, String rawToken) {
        ParsedRefreshToken parsed = parseAndHash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(parsed.tokenHash())
                .tokenJti(parsed.jti())
                .expiresAt(parsed.expiresAt())
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    private void revokeToken(RefreshToken token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(OffsetDateTime.now());
            token.setLastUsedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        }
    }

    private ParsedRefreshToken parseAndHash(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        try {
            String subject = jwtService.extractUsername(rawRefreshToken);
            if (!"refresh".equals(jwtService.extractTokenType(rawRefreshToken))) {
                throw new BadCredentialsException("Invalid refresh token");
            }
            String tokenHash = sha256Hex(rawRefreshToken);
            String jti = jwtService.extractClaim(rawRefreshToken, claims -> claims.get("jti", String.class));
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                    jwtService.extractClaim(rawRefreshToken, claims -> claims.getExpiration()).toInstant(),
                    ZoneOffset.UTC
            );
            return new ParsedRefreshToken(subject, tokenHash, jti, expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record RotationResult(User user, String refreshToken) {}

    private record ParsedRefreshToken(String subject, String tokenHash, String jti, OffsetDateTime expiresAt) {}
}
