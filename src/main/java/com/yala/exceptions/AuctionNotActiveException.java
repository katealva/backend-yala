package com.yala.exceptions;

/** Thrown when bidding on an auction that is not ACTIVE or has expired. Maps to HTTP 409. */
public class AuctionNotActiveException extends RuntimeException {

    public AuctionNotActiveException(String message) {
        super(message);
    }
}
