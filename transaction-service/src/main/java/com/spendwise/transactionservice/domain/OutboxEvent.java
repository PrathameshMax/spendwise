package com.spendwise.transactionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Milestone 16 — the Transactional Outbox row: a durable, queryable record of
 * "an event that must eventually reach Kafka," written in the exact same
 * local database transaction as the business row it describes
 * ({@link TransactionService#create}), rather than calling a Kafka producer
 * directly from inside that method. A direct second write (save to Postgres,
 * then separately call the broker) is the dual-write antipattern this
 * milestone exists to remove: the two writes are against two different
 * systems with no shared transaction, so a crash between them either loses
 * the event entirely (DB commits, broker call never happens) or fabricates
 * one with no matching committed state (broker call succeeds, DB transaction
 * then rolls back) — and there is no way to make "commit to Postgres" and
 * "publish to Kafka" atomic across two unrelated systems. Writing this row in
 * the *same* `@Transactional` boundary as the `Transaction` row means both
 * commit together or neither does: the event's existence is now a question
 * the local database transaction alone can answer, which is the basis for
 * at-least-once delivery (Q56) — a separate process (Milestone 17's own
 * outbox-publisher poller, not built yet) can always find every event that
 * genuinely happened by scanning this table, with nothing lost to a
 * mid-flight crash between the two writes.
 *
 * <p>{@code aggregateType}/{@code aggregateId}/{@code eventType}/{@code payload}
 * is the standard shape this pattern is documented with (see Debezium's own
 * "outbox event router" convention, which uses this identical column set) —
 * not an invented one: {@code aggregateType}/{@code aggregateId} identify
 * *what* changed (here, always {@code "Transaction"} and the transaction's
 * own id) independently of what kind of event it was, which matters the
 * moment this table ever carries more than one event type.
 *
 * <p>{@code processed_at} is set by Milestone 17's
 * {@code com.spendwise.transactionservice.outbox.OutboxPublisher}, through
 * {@link #markProcessed(Instant)}, only after the Kafka broker has acknowledged
 * the record (acks=all) — never before. {@code correlation_id} (Milestone 17,
 * V5 migration) carries the originating request's {@code X-Correlation-ID}
 * across the asynchronous gap between this row's write and its later publish.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload,
                       String correlationId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    /**
     * Called only after the broker has acknowledged this row's record. The
     * entity is managed inside the publisher's transaction, so dirty checking
     * flushes this as an UPDATE at commit — no explicit save needed.
     */
    public void markProcessed(Instant acknowledgedAt) {
        this.processedAt = acknowledgedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
