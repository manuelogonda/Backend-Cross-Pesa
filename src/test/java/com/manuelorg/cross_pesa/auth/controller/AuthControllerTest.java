package com.manuelorg.cross_pesa.auth.controller;

import com.manuelorg.cross_pesa.auth.dto.LoginRequest;
import com.manuelorg.cross_pesa.auth.dto.OAuth2CodeExchangeRequest;
import com.manuelorg.cross_pesa.auth.dto.RefreshTokenRequest;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.stepup.StepUpChallengeRequest;
import com.manuelorg.cross_pesa.auth.stepup.StepUpChallengeResponse;
import com.manuelorg.cross_pesa.auth.stepup.StepUpService;
import com.manuelorg.cross_pesa.auth.stepup.StepUpVerifyRequest;
import com.manuelorg.cross_pesa.auth.stepup.StepUpVerifyResponse;
import com.manuelorg.cross_pesa.auth.security.LoginRateLimiterService;
import com.manuelorg.cross_pesa.auth.service.AuthService;
import com.manuelorg.cross_pesa.auth.service.OAuth2LoginCodeService;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthService service;

    @Mock
    private StepUpService stepUpService;

    @Mock
    private LoginRateLimiterService rateLimiterService;

    @Mock
    private OAuth2LoginCodeService oauth2LoginCodeService;

    @Mock
    private UserRepository userRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(service, stepUpService, rateLimiterService, oauth2LoginCodeService, userRepository);
    }

    @Test
    void login_returnsGenericAuthenticationFailureForLockedAccount() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("Password!123")
                .build();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");

        when(rateLimiterService.tryConsume("127.0.0.1")).thenReturn(true);
        when(service.login(request)).thenThrow(new LockedException("Account is locked"));

        ResponseEntity<?> response = controller.login(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Authentication failed.");
    }

    @Test
    void refresh_returnsGenericAuthenticationFailureForDisabledAccount() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        when(service.refresh(request.refreshToken())).thenThrow(new DisabledException("Account is not active"));

        ResponseEntity<?> response = controller.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Authentication failed.");
    }

    @Test
    void exchangeOAuth2Code_returnsGenericAuthenticationFailureForInvalidCode() {
        OAuth2CodeExchangeRequest request = new OAuth2CodeExchangeRequest("code-123");
        when(oauth2LoginCodeService.consumeCode(request.code())).thenReturn(null);

        ResponseEntity<?> response = controller.exchangeOAuth2Code(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("Authentication failed.");
    }

    @Test
    void requestStepUpChallenge_delegatesToStepUpService() {
        User currentUser = User.builder().id(UUID.randomUUID()).email("alice@example.com").build();
        StepUpChallengeRequest request = new StepUpChallengeRequest(
                com.manuelorg.cross_pesa.auth.stepup.StepUpAction.TRANSACTION_SEND,
                "context"
        );
        StepUpChallengeResponse challengeResponse = new StepUpChallengeResponse(UUID.randomUUID(), null, "EMAIL");
        when(stepUpService.issueChallenge(currentUser, request.action(), request.context())).thenReturn(challengeResponse);

        ResponseEntity<StepUpChallengeResponse> response = controller.requestStepUpChallenge(currentUser, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(challengeResponse);
        verify(stepUpService).issueChallenge(currentUser, request.action(), request.context());
    }

    @Test
    void verifyStepUpChallenge_delegatesToStepUpService() {
        User currentUser = User.builder().id(UUID.randomUUID()).email("alice@example.com").build();
        StepUpVerifyRequest request = new StepUpVerifyRequest(UUID.randomUUID(), "123456");
        StepUpVerifyResponse verifyResponse = new StepUpVerifyResponse("stepup-token", java.time.OffsetDateTime.now());
        when(stepUpService.verifyChallenge(currentUser, request)).thenReturn(verifyResponse);

        ResponseEntity<StepUpVerifyResponse> response = controller.verifyStepUpChallenge(currentUser, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(verifyResponse);
        verify(stepUpService).verifyChallenge(currentUser, request);
    }
}
