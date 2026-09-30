package com.spendwise.budgetservice.client;

import java.util.UUID;

/**
 * Deliberately NOT user-service's own {@code UserResponse} DTO — that class
 * lives in user-service's own module and is not on this service's classpath,
 * consistent with the platform's "no service depends on any other service's
 * module, only spendwise-common" rule (see README, "Path to Polyrepo"). This
 * is budget-service's own minimal, consumer-owned view of the contract: it
 * only ever needs to know that a user with this id exists, so it only
 * declares the one field it actually uses. Spring Boot's Jackson
 * auto-configuration disables {@code FAIL_ON_UNKNOWN_PROPERTIES} by default,
 * so the extra fields user-service's real response body carries (email,
 * fullName, preferredCurrency, timestamps) are silently ignored during
 * deserialization rather than breaking this call.
 */
public record UserExistenceResponse(UUID id) {
}
