package com.yala.exceptions;

/** Thrown when registering with an email that is already taken. Maps to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
