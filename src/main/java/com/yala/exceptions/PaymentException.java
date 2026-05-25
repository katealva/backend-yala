package com.yala.exceptions;

/** Thrown when an external payment gateway (Stripe) fails. Maps to HTTP 502. */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
