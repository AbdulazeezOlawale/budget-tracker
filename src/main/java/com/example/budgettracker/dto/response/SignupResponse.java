package com.example.budgettracker.dto.response;

import java.time.Instant;

import com.example.budgettracker.enums.ContactMethod;

public record SignupResponse(
    Long id,
    String fullName,
    String email,
    String phoneNumber,
    ContactMethod preferredContactMethod,
    Instant createdAt,
    String tokenType,
    String token,
    long expiresIn,
    String message
) {}
