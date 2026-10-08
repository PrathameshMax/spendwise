-- Milestone 21 — the Choreographed SAGA's budget side (Functional Roadmap,
-- Workflow 2).
--
-- enforcement separates the two kinds of cap the workflow distinguishes:
-- SOFT is an alert threshold (spend may exceed it), HARD is a "do-not-exceed"
-- ceiling whose breach budget-service compensates by emitting a
-- TransactionRejectedEvent. NOT NULL DEFAULT 'SOFT' backfills every existing
-- budget to the pre-Milestone-21 behaviour — no budget silently starts
-- rejecting transactions — and keeps the default for callers that do not send
-- the new field (an additive, backward-compatible change; Q64).
ALTER TABLE budgets
    ADD COLUMN enforcement VARCHAR(10) NOT NULL DEFAULT 'SOFT',
    ADD CONSTRAINT ck_budgets_enforcement CHECK (enforcement IN ('SOFT', 'HARD'));

-- budget-service's idempotency store, same shape as notification-service's
-- (Milestone 19) and read by spendwise-common's JdbcProcessedEventGuard:
-- applying the same TransactionCreatedEvent twice would double-count spend.
CREATE TABLE processed_events (
    consumer VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
