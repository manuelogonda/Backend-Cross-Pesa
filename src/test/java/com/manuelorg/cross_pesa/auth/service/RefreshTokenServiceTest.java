package com.manuelorg.cross_pesa.auth.service;

import com.manuelorg.cross_pesa.auth.entity.RefreshToken;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.auth.repository.RefreshTokenRepository;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 60_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 86_400_000L);

        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, jwtService);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void issueRefreshToken_persistsHashedToken() throws Exception {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.issueRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken stored = captor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(stored.getUser().getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(stored.getTokenJti()).isNotBlank();
        assertThat(stored.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void rotateRefreshToken_revokesOldTokenAndIssuesNewOne() throws Exception {
        String rawToken = jwtService.generateRefreshToken(user);
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .tokenJti(jwtService.extractClaim(rawToken, claims -> claims.get("jti", String.class)))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHashForUpdate(stored.getTokenHash()))
                .thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertThat(result.user().getEmail()).isEqualTo("alice@example.com");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(rawToken);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).findByTokenHashForUpdate(stored.getTokenHash());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void rotateRefreshToken_rejectsInactiveAccountAndRevokesAllTokens() throws Exception {
        String rawToken = jwtService.generateRefreshToken(user);
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .tokenJti(jwtService.extractClaim(rawToken, claims -> claims.get("jti", String.class)))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        user.setStatus(UserStatus.SUSPENDED);

        when(refreshTokenRepository.findByTokenHashForUpdate(stored.getTokenHash()))
                .thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(DisabledException.class);

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void rotateRefreshToken_rejectsUnknownToken() {
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("not-a-jwt"))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
