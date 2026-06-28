package com.yala.exceptions;

/**
 * Thrown when DNI identity validation fails during registration: the DNI does not
 * exist, the external provider (JSON.pe) errors, or the provided names do not match
 * the official RENIEC record. Mapped to HTTP 400 by {@code GlobalExceptionsHandler}.
 */
public class IdentityValidationException extends RuntimeException {
    public IdentityValidationException(String message) {
        super(message);
    }
}
