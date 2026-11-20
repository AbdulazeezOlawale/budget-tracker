package com.olawale.budgettracker.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("This email " + email + " has already registered.");
    }
}
