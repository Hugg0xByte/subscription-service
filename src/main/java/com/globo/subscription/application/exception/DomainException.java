package com.globo.subscription.application.exception;

/**
 * Abstract base class for all domain-specific exceptions.
 * Provides a structured error code for consistent error handling across the application.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
