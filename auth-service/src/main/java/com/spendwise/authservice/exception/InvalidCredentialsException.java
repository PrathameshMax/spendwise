package com.spendwise.authservice.exception;

import com.spendwise.common.exception.SpendWiseException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends SpendWiseException {

    private static final String ERROR_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(ERROR_CODE, HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
    }
}
