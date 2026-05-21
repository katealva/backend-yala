package com.yala.exception;

/** Thrown when a listing would exceed the maximum of 5 images. Maps to HTTP 400. */
public class ImageLimitExceededException extends RuntimeException {

    public ImageLimitExceededException(String message) {
        super(message);
    }
}
