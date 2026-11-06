package com.example.budgettracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.budgettracker.dto.request.LoginRequest;
import com.example.budgettracker.dto.request.SignupRequest;
import com.example.budgettracker.dto.response.LoginResponse;
import com.example.budgettracker.dto.response.SignupResponse;
import com.example.budgettracker.entity.User;
import com.example.budgettracker.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }


    @Transactional
    public SignupResponse signup(SignupRequest request) {
        
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = new User(request.fullName().trim(), email, request.phoneNumber(), request.preferredContactMethod(), passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);

        String token = jwtService.generatedToken(savedUser);

        return new SignupResponse(
            savedUser.getId(),
            savedUser.getFullName(),
            savedUser.getEmail(),
            savedUser.getPhoneNumber(),
            savedUser.getPreferredContactMethod(),
            savedUser.getCreatedAt(),
            "Bearer",
            token,
            expirationMinutes * 60,
            "Account created successfully"
        );
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return new LoginResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            "Bearer",
            jwtService.generatedToken(user),
            expirationMinutes * 60,
            "login successful"
        );
    }
}
