package com.manuelorg.cross_pesa.auth.controller;

import com.manuelorg.cross_pesa.auth.dto.AuthResponse;
import com.manuelorg.cross_pesa.auth.dto.LoginRequest;
import com.manuelorg.cross_pesa.auth.dto.RegisterRequest;
import com.manuelorg.cross_pesa.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        // We use HTTP 201 (CREATED) to explicitly tell the frontend a new resource was made
        return new ResponseEntity<>(service.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        // HTTP 200 (OK) is standard for a successful login
        return ResponseEntity.ok(service.login(request));
    }
}