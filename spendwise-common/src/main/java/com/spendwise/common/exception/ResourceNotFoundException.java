package com.spendwise.common.exception;

public class ResourceNotFoundException extends SpendWiseException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(ERROR_CODE, "%s not found for identifier '%s'".formatted(resourceName, identifier));
    }
}
