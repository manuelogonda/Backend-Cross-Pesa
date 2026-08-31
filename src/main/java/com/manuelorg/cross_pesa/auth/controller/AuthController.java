package com.manuelorg.cross_pesa.auth.controller;

import com.manuelorg.cross_pesa.auth.dto.AuthResponse;
import com.manuelorg.cross_pesa.auth.dto.LoginRequest;
import com.manuelorg.cross_pesa.auth.dto.OAuth2CodeExchangeRequest;
import com.manuelorg.cross_pesa.auth.dto.RefreshTokenRequest;
import com.manuelorg.cross_pesa.auth.dto.RegisterRequest;
import com.manuelorg.cross_pesa.auth.security.LoginRateLimiterService;
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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        try {
            return ResponseEntity.ok(service.refresh(request.refreshToken()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Your account is currently suspended. Please contact support."));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token."));
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
    public ResponseEntity<AuthResponse> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2CodeExchangeRequest request
    ) {
        String email = oauth2LoginCodeService.consumeCode(request.code());
        if (email == null) {
            throw new BadCredentialsException("Invalid or expired OAuth2 login code.");
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OAuth2 login code."));

        return ResponseEntity.ok(service.issueTokens(user));
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
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", "Your account is currently suspended. Please contact support."));
        } catch (org.springframework.security.authentication.LockedException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("error", "Your account is locked."));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("error", "Invalid email or password."));
        }
    }
}
