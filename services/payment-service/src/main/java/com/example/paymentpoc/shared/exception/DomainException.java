package com.example.paymentpoc.shared.exception;

/**
 * Base for domain business-rule violations. Subclasses live in feature packages;
 * the handler in shared can respond without importing feature types.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final int httpStatusCode;
    private final String target;

    protected DomainException(String errorCode, int httpStatusCode, String message) {
        this(errorCode, httpStatusCode, message, null);
    }

    protected DomainException(String errorCode, int httpStatusCode, String message, String target) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
        this.target = target;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getTarget() {
        return target;
    }
}
