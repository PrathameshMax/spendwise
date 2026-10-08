package com.spendwise.analyticsservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 21 — analytics-service's own copy of transaction-service's
 * {@code TransactionReversedEvent} (per-consumer contracts, tolerant reader).
 * It carries the same dimensions as {@link TransactionCreatedEvent} — user,
 * month, category, type, amounts — because retracting an entry from a
 * projection means subtracting exactly what ingesting it added, without
 * calling back to transaction-service to look it up (Functional Roadmap,
 * Workflow 2, Step 6).
 *
 * @param transactionId the reversed entry, the one previously ingested
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionReversedEvent(
        UUID transactionId,
        UUID reversalTransactionId,
        UUID userId,
        UUID categoryId,
        BigDecimal amount,
        String type,
        LocalDate transactionDate,
        String currency,
        BigDecimal baseCurrencyAmount,
        String reason) {
}
