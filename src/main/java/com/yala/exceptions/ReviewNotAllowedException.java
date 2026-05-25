package com.yala.exceptions;

/** Thrown when reviewing an order that is not yet CONFIRMED. Maps to HTTP 403. */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
