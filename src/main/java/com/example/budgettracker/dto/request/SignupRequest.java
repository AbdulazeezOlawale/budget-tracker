package com.example.budgettracker.dto.request;

import com.example.budgettracker.enums.ContactMethod;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank(message = "Full name is required") @Size(max = 120, message = "Full name cannot exceed 120 characters") String fullName,

    @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 254, message = "Email is too long") String email,

    @NotBlank(message = "Phone number is required") @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Use a phone number, e.g. +2348012345678") String phoneNumber,

    @NotNull(message = "Preferred contact method is required") ContactMethod preferredContactMethod,

    @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters") String password
) {}