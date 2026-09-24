package com.spendwise.common.exception;

public class DuplicateResourceException extends SpendWiseException {

    private static final String ERROR_CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(ERROR_CODE, "%s with %s '%s' already exists".formatted(resourceName, field, value));
    }
}
