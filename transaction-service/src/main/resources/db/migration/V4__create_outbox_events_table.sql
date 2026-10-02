-- Milestone 16 — the Transactional Outbox table. `payload` is the
-- serialized event body (JSON, via Jackson) rather than a typed column per
-- field: this table's own schema must stay stable even as event payload
-- shapes evolve, the same reason an event log is append-only free-form
-- rather than relationally normalized. `processed_at` stays NULL for every
-- row through this milestone — nothing reads or publishes this table yet,
-- that is Milestone 17's own background outbox-publisher poller, which will
-- mark a row complete only on Kafka broker acknowledgment. The partial index
-- below exists now, alongside the column it serves, for that same future
-- poller's own "unprocessed rows oldest first" query — unused until then,
-- but it is the column's own natural index, not a substep borrowed from a
-- later milestone.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_events_unprocessed ON outbox_events (created_at) WHERE processed_at IS NULL;
