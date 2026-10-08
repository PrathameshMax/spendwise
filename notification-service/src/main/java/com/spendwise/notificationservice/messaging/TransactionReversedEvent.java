package com.spendwise.notificationservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 21 — notification-service's own copy of transaction-service's
 * {@code TransactionReversedEvent} (per-consumer contracts, tolerant reader),
 * holding only what the user's rejection notice says (Functional Roadmap,
 * Workflow 2, Step 5).
 *
 * @param transactionId the reversed entry, as the user knows it
 * @param reason        why budget-service rejected it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionReversedEvent(
        UUID transactionId,
        UUID userId,
        String categoryName,
        BigDecimal amount,
        String type,
        LocalDate transactionDate,
        String currency,
        String reason) {
}
