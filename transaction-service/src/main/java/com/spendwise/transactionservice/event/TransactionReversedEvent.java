package com.spendwise.transactionservice.event;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 21 — the saga's final fact (Functional Roadmap, Workflow 2,
 * Step 4): transaction-service has reversed a ledger entry. Written to the
 * outbox in the same transaction as the reversal and published to
 * {@code transaction-events} keyed by the <em>original</em> transaction's id,
 * so it lands on the same partition as, and after, that transaction's
 * {@code TransactionCreatedEvent}: a consumer never sees a reversal before
 * the entry it reverses.
 *
 * <p>Carries what consumers need without calling back: notification-service
 * tells the user what was rejected and why (Step 5); analytics-service
 * retracts the original amount from its read side (Step 6).
 *
 * @param transactionId          the reversed (original) entry
 * @param reversalTransactionId  the compensating entry written beside it
 * @param amount                 the original entry's amount, positive
 * @param baseCurrencyAmount     the original's base-currency amount, if converted
 * @param reason                 why it was reversed, from budget-service
 */
public record TransactionReversedEvent(
        UUID transactionId,
        UUID reversalTransactionId,
        UUID userId,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        TransactionType type,
        LocalDate transactionDate,
        String currency,
        BigDecimal baseCurrencyAmount,
        String reason,
        Instant reversedAt) {
}
