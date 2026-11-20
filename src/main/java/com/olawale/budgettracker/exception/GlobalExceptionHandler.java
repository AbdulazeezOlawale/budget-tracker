package com.olawale.budgettracker.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

        // Bad request body → 400 with per-field messages
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
                Map<String, String> fields = new LinkedHashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));

                var body = new ApiError(HttpStatus.BAD_REQUEST.value(),
                                "Validation failed", fields, Instant.now());
                return ResponseEntity.badRequest().body(body);
        }

        // Email taken → 409
        @ExceptionHandler(EmailAlreadyExistsException.class)
        public ResponseEntity<ApiError> handleConflict(EmailAlreadyExistsException ex) {
                var body = new ApiError(HttpStatus.CONFLICT.value(),
                                ex.getMessage(), Map.of(), Instant.now());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        @ExceptionHandler (InvalidCredentialsException.class)
        public ResponseEntity<ApiError> handleBadCredentials(InvalidCredentialsException ex) {
                var body = new ApiError(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), Map.of(), Instant.now());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(InvalidTokenException.class)
        public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex) {
                var body = new ApiError(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), Map.of(), Instant.now());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        public record ApiError(int status, String message,
                        Map<String, String> fieldErrors, Instant timestamp) {
        }
}