package com.yala.exceptions;

import java.time.LocalDateTime;

/** Consistent error payload returned by {@link GlobalExceptionHandler} on every failure. */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
}
