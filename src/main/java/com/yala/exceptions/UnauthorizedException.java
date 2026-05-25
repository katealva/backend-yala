package com.yala.exceptions;

/** Thrown on authentication failures (e.g. invalid credentials). Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
