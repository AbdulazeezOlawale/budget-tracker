package com.example.budgettracker.dto.response;

public record LoginResponse(
    Long id,
    String fullName,
    String email,
    String tokenType,
    String token,
    long expiresIn,
    String message
) {}
