-- Milestone 21 — the ledger side of the budget-breach saga (Functional
-- Roadmap, Workflow 2, Step 4). The ledger is append-only: a rejected
-- transaction is never deleted or edited in amount; it is marked REVERSED and
-- a second, reversing entry with the negated amount is written beside it.
--
--   POSTED    an ordinary entry (every row before this migration, and every
--             new transaction when it is created)
--   REVERSED  a POSTED entry that has since been compensated
--   REVERSAL  the compensating entry itself; reverses_transaction_id points
--             at the entry it cancels and reversal_reason says why
--
-- NOT NULL DEFAULT 'POSTED' backfills existing rows and keeps inserts that
-- do not name a status (an instance still on the previous release, during a
-- rolling deploy) valid — an additive change (Q64).
ALTER TABLE transactions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    ADD COLUMN reverses_transaction_id UUID,
    ADD COLUMN reversal_reason VARCHAR(500),
    ADD CONSTRAINT ck_transactions_status CHECK (status IN ('POSTED', 'REVERSED', 'REVERSAL')),
    ADD CONSTRAINT fk_transactions_reverses FOREIGN KEY (reverses_transaction_id) REFERENCES transactions (id),
    ADD CONSTRAINT ck_transactions_reversal_link
        CHECK ((status = 'REVERSAL') = (reverses_transaction_id IS NOT NULL));

-- At most one reversing entry per original, enforced by the database as well
-- as by the idempotency guard and the status check in
-- TransactionReversalService: a second reversal would double the
-- compensation. Also the index for "find the reversal of X".
CREATE UNIQUE INDEX uk_transactions_reverses_transaction_id
    ON transactions (reverses_transaction_id) WHERE reverses_transaction_id IS NOT NULL;

-- transaction-service becomes a consumer at this milestone (budget-events),
-- so it gets the platform's idempotency store, same shape as every other
-- consumer's and read by spendwise-common's JdbcProcessedEventGuard.
CREATE TABLE processed_events (
    consumer VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
