package com.manuelorg.cross_pesa.auth.controller;

import com.manuelorg.cross_pesa.auth.dto.AuthResponse;
import com.manuelorg.cross_pesa.auth.dto.LoginRequest;
import com.manuelorg.cross_pesa.auth.dto.OAuth2CodeExchangeRequest;
import com.manuelorg.cross_pesa.auth.dto.RefreshTokenRequest;
import com.manuelorg.cross_pesa.auth.dto.RegisterRequest;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.security.LoginRateLimiterService;
import com.manuelorg.cross_pesa.auth.stepup.StepUpChallengeRequest;
import com.manuelorg.cross_pesa.auth.stepup.StepUpChallengeResponse;
import com.manuelorg.cross_pesa.auth.stepup.StepUpService;
import com.manuelorg.cross_pesa.auth.stepup.StepUpVerifyRequest;
import com.manuelorg.cross_pesa.auth.stepup.StepUpVerifyResponse;
import com.manuelorg.cross_pesa.auth.service.AuthService;
import com.manuelorg.cross_pesa.auth.service.OAuth2LoginCodeService;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final StepUpService stepUpService;
    private final LoginRateLimiterService rateLimiterService;
    private final OAuth2LoginCodeService oauth2LoginCodeService;
    private final UserRepository userRepository;


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        // We use HTTP 201 (CREATED) to explicitly tell the frontend a new resource was made
        return new ResponseEntity<>(service.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/step-up/challenge")
    public ResponseEntity<StepUpChallengeResponse> requestStepUpChallenge(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody StepUpChallengeRequest request
    ) {
        requireAuthenticatedUser(currentUser);
        return ResponseEntity.ok(stepUpService.issueChallenge(currentUser, request.action(), request.context()));
    }

    @PostMapping("/step-up/verify")
    public ResponseEntity<StepUpVerifyResponse> verifyStepUpChallenge(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody StepUpVerifyRequest request
    ) {
        requireAuthenticatedUser(currentUser);
        return ResponseEntity.ok(stepUpService.verifyChallenge(currentUser, request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        try {
            return ResponseEntity.ok(service.refresh(request.refreshToken()));
        } catch (AuthenticationException e) {
            return authenticationFailure();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth2/exchange")
    public ResponseEntity<?> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2CodeExchangeRequest request
    ) {
        try {
            String email = oauth2LoginCodeService.consumeCode(request.code());
            if (email == null) {
                throw new BadCredentialsException("Authentication failed.");
            }

            var user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Authentication failed."));

            return ResponseEntity.ok(service.issueTokens(user));
        } catch (AuthenticationException e) {
            return authenticationFailure();
        }
    }



    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        // 1. Get the IP address
        String ipAddress = servletRequest.getRemoteAddr();

        // 2. Try to consume a token via Redisson
        if (!rateLimiterService.tryConsume(ipAddress)) {
            // Token rejected - block the attempt
            throw new RateLimitExceededException("Too many login attempts. Please try again in 15 minutes.");
        }
        try {
            // HTTP 200 (OK) is standard for a successful login
            return ResponseEntity.ok(service.login(request));
        } catch (AuthenticationException e) {
            return authenticationFailure();
        }
    }

    private ResponseEntity<Map<String, String>> authenticationFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication failed."));
    }

    private void requireAuthenticatedUser(User currentUser) {
        if (currentUser == null) {
            throw new BadCredentialsException("Authentication failed.");
        }
    }
}
