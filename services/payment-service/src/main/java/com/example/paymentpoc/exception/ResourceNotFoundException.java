package com.example.paymentpoc.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, String id) {
        super(resourceType + " not found: " + id);
    }
}
