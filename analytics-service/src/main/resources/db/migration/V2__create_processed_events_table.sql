-- Milestone 19 — analytics-service's idempotency store, same shape and same
-- claim mechanism as notification-service's: ProcessedEventGuard inserts with
-- ON CONFLICT DO NOTHING and reads the row count (1 = first delivery,
-- 0 = already processed). Lives in analytics_db, next to the projections
-- Milestone 23 builds, so a projection update and its claim can commit in one
-- transaction.
CREATE TABLE processed_events (
    consumer VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
