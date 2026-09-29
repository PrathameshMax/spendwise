package com.spendwise.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessValidationException extends SpendWiseException {

    private static final String ERROR_CODE = "BUSINESS_VALIDATION_FAILED";

    public BusinessValidationException(String message) {
        super(ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
