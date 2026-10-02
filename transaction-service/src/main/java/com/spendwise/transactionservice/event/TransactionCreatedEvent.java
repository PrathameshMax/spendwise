package com.spendwise.transactionservice.event;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 16 — the event this platform's own roadmap names in advance of
 * building its consumers ("TransactionCreatedEvent," referenced ahead of this
 * milestone and again at Milestone 19's idempotency layer, which checks for
 * redelivery of exactly this event type): the fact that a {@code Transaction}
 * was created, carrying everything a future downstream consumer
 * (notification-service, analytics-service, budget-service's own
 * {@code currentSpend} update — none of them built yet, all deferred to
 * Milestone 17 onward once a real Kafka consumer exists to receive this) would
 * need, without that consumer having to call back into transaction-service to
 * fetch anything further.
 *
 * <p>This is a plain record, not yet a member of the sealed-interface event
 * hierarchy an earlier roadmap note sketches for listener-side pattern
 * matching — no listener exists anywhere on this platform yet to pattern-match
 * against, so building that hierarchy now would be exactly the kind of
 * substep this milestone's own scope (the outbox table and the atomic write)
 * doesn't ask for; it is introduced the milestone a real consumer needs it.
 *
 * <p>Deliberately local to transaction-service, not spendwise-common: this
 * platform's own established convention (budget-service's
 * {@code UserExistenceResponse}, analytics-service's duplicated
 * {@code budget_summary.proto}) is that a shared module stays free of
 * business/domain-shaped contracts. The question of whether a future
 * consumer duplicates this shape or the two share one is Milestone 17+'s
 * to answer, once a consumer actually exists to have an opinion.
 */
public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        UUID categoryId,
        BigDecimal amount,
        TransactionType type,
        LocalDate transactionDate,
        String currency,
        BigDecimal baseCurrencyAmount,
        Instant createdAt) {
}
