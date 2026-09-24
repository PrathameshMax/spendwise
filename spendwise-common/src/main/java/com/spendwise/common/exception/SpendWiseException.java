package com.spendwise.common.exception;

/**
 * Root of the platform's exception hierarchy. Every service-specific exception
 * extends this so a future global handler (Milestone 7) can translate any of
 * them into a single, consistent error contract without a per-service switch.
 */
public abstract class SpendWiseException extends RuntimeException {

    private final String errorCode;

    protected SpendWiseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected SpendWiseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
