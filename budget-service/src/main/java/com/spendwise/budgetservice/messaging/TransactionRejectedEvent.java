package com.spendwise.budgetservice.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Milestone 21 — the compensating event of the budget-breach saga
 * (Functional Roadmap, Workflow 2, Step 3): budget-service's decision that a
 * transaction transaction-service has already committed violates a HARD cap
 * and must be reversed. Published to {@code budget-events}, keyed by
 * {@code transactionId}.
 *
 * <p>It states a fact and the evidence for it — which budget, what the cap
 * and spend were, what the expense would have made it — rather than issuing a
 * command. transaction-service decides how to compensate (a reversing ledger
 * entry), and other consumers of {@code budget-events} can use the same fact
 * without knowing anything about the ledger.
 *
 * @param reason human-readable rejection reason, carried through to
 *               transaction-service's {@code reversal_reason} and the user's
 *               notification
 */
public record TransactionRejectedEvent(
        UUID transactionId,
        UUID userId,
        UUID budgetId,
        String category,
        YearMonth periodMonth,
        BigDecimal cappedAmount,
        BigDecimal currentSpend,
        BigDecimal attemptedAmount,
        String reason,
        Instant rejectedAt) {
}
