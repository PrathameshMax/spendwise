package com.spendwise.userservice.api;

/**
 * Both fields are nullable — this is a partial update; a null field leaves the
 * existing value untouched. Bean Validation groups for create-vs-update semantics
 * are formalized in Milestone 8.
 */
public record UpdateUserRequest(String fullName, String preferredCurrency) {
}
