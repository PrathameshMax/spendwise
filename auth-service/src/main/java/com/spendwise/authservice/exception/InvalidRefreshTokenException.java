package com.spendwise.authservice.exception;

import com.spendwise.common.exception.SpendWiseException;

public class InvalidRefreshTokenException extends SpendWiseException {

    private static final String ERROR_CODE = "INVALID_REFRESH_TOKEN";

    public InvalidRefreshTokenException(String reason) {
        super(ERROR_CODE, "Refresh token rejected: %s".formatted(reason));
    }
}
