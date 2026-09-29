package com.spendwise.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the platform's exception hierarchy. Every service-specific exception
 * extends this so the shared {@link AbstractGlobalExceptionHandler} (Milestone 7)
 * can translate any of them into a single, consistent RFC 7807 ProblemDetail
 * contract without a per-exception-type switch in the handler itself — each
 * subclass simply states the HTTP status and machine-readable error code that
 * belong to it.
 */
public abstract class SpendWiseException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected SpendWiseException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected SpendWiseException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
