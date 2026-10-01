package com.spendwise.transactionservice.client;

import java.util.UUID;

/**
 * Deliberately NOT user-service's own {@code UserResponse} DTO — that class
 * lives in user-service's own module and is not on this service's classpath,
 * consistent with the platform's "no service depends on any other service's
 * module, only spendwise-common" rule (see README, "Path to Polyrepo"). This
 * is transaction-service's own minimal, consumer-owned view of the contract:
 * it only declares the fields it actually uses. Spring Boot's Jackson
 * auto-configuration disables {@code FAIL_ON_UNKNOWN_PROPERTIES} by default,
 * so the remaining fields user-service's real response body carries (email,
 * fullName, timestamps) are silently ignored during deserialization rather
 * than breaking this call.
 *
 * <p>{@code preferredCurrency} (Milestone 13) was added once there was an
 * actual consumer for it: {@link com.spendwise.transactionservice.service
 * .TransactionService} needs the user's base currency to decide whether a
 * transaction needs converting at all, before ever calling
 * {@code ExchangeRateClient}. No change was needed on user-service's side —
 * its response already carried this field; this consumer simply wasn't
 * declaring it yet.
 */
public record UserExistenceResponse(UUID id, String preferredCurrency) {
}
