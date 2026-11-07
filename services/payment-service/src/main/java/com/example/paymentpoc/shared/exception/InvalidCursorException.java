package com.example.paymentpoc.shared.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid pagination cursor: " + cursor);
    }
}
