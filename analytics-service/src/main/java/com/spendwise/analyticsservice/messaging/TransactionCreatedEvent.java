package com.spendwise.analyticsservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 18 — analytics-service's own copy of the payload
 * transaction-service publishes (per-consumer contracts, as everywhere else on
 * this platform). It deliberately differs from notification-service's copy:
 * each consumer declares only the fields its own purpose needs. Analytics
 * keeps {@code categoryId} and {@code baseCurrencyAmount}, which the
 * category-breakdown and monthly-trend read models (Milestone 23) aggregate
 * on; notification-service has no use for either.
 *
 * <p>Tolerant reader: unknown fields are ignored and {@code type} is a plain
 * {@code String}, so producer-side additions cannot break this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        UUID categoryId,
        BigDecimal amount,
        String type,
        LocalDate transactionDate,
        String currency,
        BigDecimal baseCurrencyAmount,
        Instant createdAt) {
}
