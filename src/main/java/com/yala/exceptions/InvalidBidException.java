package com.yala.exceptions;

/** Thrown when a bid amount is not strictly greater than the current price. Maps to HTTP 400. */
public class InvalidBidException extends RuntimeException {

    public InvalidBidException(String message) {
        super(message);
    }
}
