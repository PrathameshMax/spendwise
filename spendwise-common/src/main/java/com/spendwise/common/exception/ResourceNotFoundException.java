package com.spendwise.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends SpendWiseException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, "%s not found for identifier '%s'".formatted(resourceName, identifier));
    }
}
