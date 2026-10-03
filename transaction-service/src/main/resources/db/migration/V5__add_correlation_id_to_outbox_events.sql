-- Milestone 17 — carries the originating HTTP request's X-Correlation-ID
-- across the asynchronous boundary the outbox introduces. The row is written
-- on the request thread (where CorrelationIdFilter has populated the MDC) but
-- published later by OutboxPublisher on a scheduler thread that has no request
-- context at all; without persisting the id here, every Kafka record this
-- table produces would be untraceable back to the API call that caused it.
-- Nullable: rows written before this migration (Milestone 16) have none, and
-- a write path with no inbound request (none exists today) would have none
-- either — the publisher simply omits the header in that case.
ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(64);
