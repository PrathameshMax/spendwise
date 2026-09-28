package com.spendwise.authservice.exception;

import com.spendwise.common.exception.SpendWiseException;

public class InvalidCredentialsException extends SpendWiseException {

    private static final String ERROR_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(ERROR_CODE, "Email or password is incorrect");
    }
}
