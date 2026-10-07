-- Milestone 19 — notification-service's first schema.
--
-- processed_events is the idempotency store: one row per (consumer, event)
-- this service has already acted on. The composite primary key is the whole
-- mechanism — ProcessedEventGuard inserts with ON CONFLICT DO NOTHING inside
-- the same transaction as the side effect, and the row count it gets back
-- (1 = first delivery, 0 = already processed) decides whether to proceed.
-- `consumer` is the listener's name, not the Kafka group: two listeners in
-- one service consuming the same event for different purposes must each be
-- able to process it once.
CREATE TABLE processed_events (
    consumer VARCHAR(100) NOT NULL,
    event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

-- The record of each alert this service raised — its side effect, committed
-- atomically with the processed_events claim. event_id links an alert back
-- to the Kafka event (the outbox row id) that caused it; it is not unique
-- here because uniqueness is processed_events' job, and one event may one day
-- produce alerts on several channels.
CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    event_id VARCHAR(200) NOT NULL,
    user_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_log_transaction_id ON notification_log (transaction_id);
