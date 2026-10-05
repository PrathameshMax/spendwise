package com.spendwise.notificationservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 18 — notification-service's own copy of the payload
 * transaction-service publishes, following this platform's per-consumer
 * contract convention ({@code UserExistenceResponse}, the duplicated
 * {@code budget_summary.proto}): the producer's class is not shared.
 *
 * <p>Written as a tolerant reader. {@code ignoreUnknown} means the producer can
 * add fields without breaking this consumer, and {@code type} is a plain
 * {@code String} rather than a copy of transaction-service's
 * {@code TransactionType} enum, so a new type value on the producer side
 * cannot fail deserialization here. Fields this service does not use are
 * simply left out of the record. Milestone 22 replaces this informal
 * agreement with a registered schema.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        BigDecimal amount,
        String type,
        LocalDate transactionDate,
        String currency,
        Instant createdAt) {
}
