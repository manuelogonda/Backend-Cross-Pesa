package com.manuelorg.cross_pesa.auth.stepup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.exception.RateLimitExceededException;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepUpService {

    public static final String STEP_UP_TOKEN_HEADER = "X-Step-Up-Token";
    private static final String CHALLENGE_KEY_PREFIX = "stepup:challenge:";
    private static final String TOKEN_KEY_PREFIX = "stepup:token:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final RedissonClient redissonClient;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public StepUpChallengeResponse issueChallenge(User user, StepUpAction action, String context) {
        validateUserAndPayload(user, action, context);
        rateLimitChallengeRequests(user.getId(), action);

        UUID challengeId = UUID.randomUUID();
        String code = generateCode();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(CHALLENGE_TTL);

        StoredChallenge challenge = new StoredChallenge(
                user.getId(),
                action,
                context,
                passwordEncoder.encode(code),
                expiresAt.toString(),
                0
        );
        writeChallenge(challengeId, challenge);
        publishOtpEmail(user, action, code, expiresAt, challengeId, context);

        log.info("Issued step-up challenge for user {} action {} challengeId={}", user.getId(), action, challengeId);
        return new StepUpChallengeResponse(challengeId, expiresAt, "EMAIL");
    }

    public StepUpVerifyResponse verifyChallenge(User user, StepUpVerifyRequest request) {
        if (user == null) {
            throw new StepUpRequiredException("Authentication required");
        }

        StoredChallenge challenge = readChallenge(request.challengeId())
                .orElseThrow(() -> new StepUpVerificationException("Step-up verification failed"));

        if (!challenge.userId().equals(user.getId())
                || OffsetDateTime.parse(challenge.expiresAt()).isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new StepUpVerificationException("Step-up verification failed");
        }

        if (!passwordEncoder.matches(request.code(), challenge.codeHash())) {
            int attempts = challenge.attempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                deleteChallenge(request.challengeId());
            } else {
                writeChallenge(request.challengeId(), challenge.withAttempts(attempts));
            }
            throw new StepUpVerificationException("Step-up verification failed");
        }

        deleteChallenge(request.challengeId());

        String rawToken = generateToken();
        OffsetDateTime tokenExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL);
        StoredToken token = new StoredToken(user.getId(), challenge.action(), challenge.context(), tokenExpiresAt.toString());
        writeToken(rawToken, token);

        log.info("Verified step-up challenge for user {} action {} tokenIssued", user.getId(), challenge.action());
        return new StepUpVerifyResponse(rawToken, tokenExpiresAt);
    }

    public void requireStepUp(User user, StepUpAction action, String context, String rawToken) {
        validateUserAndPayload(user, action, context);

        if (rawToken == null || rawToken.isBlank()) {
            throw new StepUpRequiredException("Step-up verification required");
        }

        StoredToken token = readToken(rawToken)
                .orElseThrow(() -> new StepUpVerificationException("Step-up verification failed"));

        if (!token.userId().equals(user.getId())
                || token.action() != action
                || !token.context().equals(context)
                || OffsetDateTime.parse(token.expiresAt()).isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new StepUpVerificationException("Step-up verification failed");
        }

        deleteToken(rawToken);
    }

    private void rateLimitChallengeRequests(UUID userId, StepUpAction action) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("stepup_challenge:" + userId + ":" + action.name());
        rateLimiter.trySetRate(org.redisson.api.RateType.OVERALL, 3, 10, org.redisson.api.RateIntervalUnit.MINUTES);
        if (!rateLimiter.tryAcquire(1)) {
            throw new RateLimitExceededException("Too many step-up requests. Please try again later.");
        }
    }

    private void publishOtpEmail(User user, StepUpAction action, String code, OffsetDateTime expiresAt, UUID challengeId, String context) {
        eventPublisher.publishEvent(new TriggerNotificationEvent(
                user.getId(),
                null,
                "Cross-Pesa step-up verification code",
                "Your verification code is " + code + ". It expires at " + expiresAt + ".",
                NotificationType.EMAIL,
                Map.of(
                        "stepUpChallengeId", challengeId.toString(),
                        "stepUpAction", action.name(),
                        "stepUpContext", context,
                        "stepUpExpiresAt", expiresAt.toString()
                )
        ));
    }

    private void validateUserAndPayload(User user, StepUpAction action, String context) {
        if (user == null) {
            throw new StepUpRequiredException("Authentication required");
        }
        if (action == null) {
            throw new IllegalArgumentException("Step-up action is required");
        }
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Step-up context is required");
        }
    }

    private String generateCode() {
        int value = 100000 + secureRandom.nextInt(900000);
        return Integer.toString(value);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void writeChallenge(UUID challengeId, StoredChallenge challenge) {
        try {
            String payload = objectMapper.writeValueAsString(challenge);
            bucket(CHALLENGE_KEY_PREFIX + challengeId).set(payload, CHALLENGE_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist step-up challenge", e);
        }
    }

    private java.util.Optional<StoredChallenge> readChallenge(UUID challengeId) {
        try {
            String payload = bucket(CHALLENGE_KEY_PREFIX + challengeId).get();
            if (payload == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(objectMapper.readValue(payload, StoredChallenge.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load step-up challenge", e);
        }
    }

    private void deleteChallenge(UUID challengeId) {
        bucket(CHALLENGE_KEY_PREFIX + challengeId).delete();
    }

    private void writeToken(String rawToken, StoredToken token) {
        try {
            String tokenHash = sha256Hex(rawToken);
            String payload = objectMapper.writeValueAsString(token);
            bucket(TOKEN_KEY_PREFIX + tokenHash).set(payload, TOKEN_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist step-up token", e);
        }
    }

    private java.util.Optional<StoredToken> readToken(String rawToken) {
        try {
            String tokenHash = sha256Hex(rawToken);
            String payload = bucket(TOKEN_KEY_PREFIX + tokenHash).get();
            if (payload == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(objectMapper.readValue(payload, StoredToken.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load step-up token", e);
        }
    }

    private void deleteToken(String rawToken) {
        bucket(TOKEN_KEY_PREFIX + sha256Hex(rawToken)).delete();
    }

    private RBucket<String> bucket(String key) {
        return redissonClient.getBucket(key);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record StoredChallenge(
            UUID userId,
            StepUpAction action,
            String context,
            String codeHash,
            String expiresAt,
            int attempts
    ) {
        StoredChallenge withAttempts(int attempts) {
            return new StoredChallenge(userId, action, context, codeHash, expiresAt, attempts);
        }
    }

    private record StoredToken(
            UUID userId,
            StepUpAction action,
            String context,
            String expiresAt
    ) {}
}
