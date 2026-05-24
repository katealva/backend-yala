package com.yala.exceptions;

/** Thrown when confirming an order that is already CONFIRMED or CANCELLED. Maps to HTTP 409. */
public class OrderNotConfirmableException extends RuntimeException {

    public OrderNotConfirmableException(String message) {
        super(message);
    }
}
