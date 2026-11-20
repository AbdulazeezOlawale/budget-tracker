package com.olawale.budgettracker.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.olawale.budgettracker.dto.auth.AuthResponse;
import com.olawale.budgettracker.dto.register.RegisterRequest;
import com.olawale.budgettracker.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegisterController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 201: a user was created. The body is the same contract login/refresh return.
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
