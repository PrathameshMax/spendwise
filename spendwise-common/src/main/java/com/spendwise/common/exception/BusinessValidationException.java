package com.spendwise.common.exception;

public class BusinessValidationException extends SpendWiseException {

    private static final String ERROR_CODE = "BUSINESS_VALIDATION_FAILED";

    public BusinessValidationException(String message) {
        super(ERROR_CODE, message);
    }
}
