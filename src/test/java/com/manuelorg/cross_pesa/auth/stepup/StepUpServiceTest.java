package com.manuelorg.cross_pesa.auth.stepup;

import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepUpService Tests")
class StepUpServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RRateLimiter rateLimiter;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();
    private final Map<String, RBucket<String>> buckets = new ConcurrentHashMap<>();

    private StepUpService stepUpService;
    private User user;

    @BeforeEach
    void setUp() {
        stepUpService = new StepUpService(redissonClient, passwordEncoder, eventPublisher);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .role(Role.USER)
                .build();

        lenient().when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        lenient().when(rateLimiter.tryAcquire(1)).thenReturn(true);
        lenient().when(rateLimiter.trySetRate(eq(RateType.OVERALL), anyLong(), anyLong(), eq(RateIntervalUnit.MINUTES))).thenReturn(true);
        lenient().when(redissonClient.getBucket(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return buckets.computeIfAbsent(key, this::createBucket);
        });
    }

    @Test
    void issueChallenge_emailsOtpAndPersistsChallenge() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        StepUpChallengeResponse response = stepUpService.issueChallenge(
                user,
                StepUpAction.TRANSACTION_SEND,
                "context-123"
        );

        assertThat(response.challengeId()).isNotNull();
        assertThat(response.delivery()).isEqualTo("EMAIL");
        assertThat(response.expiresAt()).isAfter(OffsetDateTime.now());

        ArgumentCaptor<TriggerNotificationEvent> captor = ArgumentCaptor.forClass(TriggerNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TriggerNotificationEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(com.manuelorg.cross_pesa.notification.enums.NotificationType.EMAIL);
        assertThat(event.message()).contains("verification code");
        assertThat(redisStore).containsKey("stepup:challenge:" + response.challengeId());
    }

    @Test
    void verifyChallenge_returnsTokenAndConsumesItOnce() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        StepUpChallengeResponse challenge = stepUpService.issueChallenge(
                user,
                StepUpAction.TRANSACTION_SEND,
                "sourceWalletId=wallet-1;beneficiaryId=beneficiary-1"
        );

        ArgumentCaptor<TriggerNotificationEvent> captor = ArgumentCaptor.forClass(TriggerNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        String rawCode = extractSixDigitCode(captor.getValue().message());

        when(passwordEncoder.matches(eq(rawCode), eq("hashed-code"))).thenReturn(true);

        StepUpVerifyResponse verified = stepUpService.verifyChallenge(
                user,
                new StepUpVerifyRequest(challenge.challengeId(), rawCode)
        );

        assertThat(verified.stepUpToken()).isNotBlank();
        assertThat(verified.expiresAt()).isAfter(OffsetDateTime.now());

        stepUpService.requireStepUp(
                user,
                StepUpAction.TRANSACTION_SEND,
                "sourceWalletId=wallet-1;beneficiaryId=beneficiary-1",
                verified.stepUpToken()
        );

        assertThatThrownBy(() -> stepUpService.requireStepUp(
                user,
                StepUpAction.TRANSACTION_SEND,
                "sourceWalletId=wallet-1;beneficiaryId=beneficiary-1",
                verified.stepUpToken()
        )).isInstanceOf(StepUpVerificationException.class);
    }

    @Test
    void requireStepUp_withoutTokenFails() {
        assertThatThrownBy(() -> stepUpService.requireStepUp(
                user,
                StepUpAction.ADMIN_TREASURY_REBALANCE,
                "sourceCurrency=KES",
                null
        )).isInstanceOf(StepUpRequiredException.class);
    }

    private RBucket<String> createBucket(String key) {
        RBucket<String> bucket = mock(RBucket.class);
        doAnswer(invocation -> {
            redisStore.put(key, invocation.getArgument(0, String.class));
            return null;
        }).when(bucket).set(anyString(), any(java.time.Duration.class));
        lenient().when(bucket.get()).thenAnswer(invocation -> redisStore.get(key));
        lenient().doAnswer(invocation -> {
            redisStore.remove(key);
            return true;
        }).when(bucket).delete();
        return bucket;
    }

    private static String extractSixDigitCode(String message) {
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(message);
        if (!matcher.find()) {
            throw new AssertionError("No OTP found in message: " + message);
        }
        return matcher.group(1);
    }
}
