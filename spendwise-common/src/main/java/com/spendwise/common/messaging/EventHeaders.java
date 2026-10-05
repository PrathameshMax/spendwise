package com.spendwise.common.messaging;

/**
 * Milestone 18 — Kafka record header names that make up this platform's event
 * envelope, shared by the producer (transaction-service's OutboxPublisher,
 * Milestone 17) and every consumer (Milestone 18 onward).
 *
 * <p>These live in spendwise-common, unlike event payload types, because they
 * are transport metadata rather than a business contract: every topic and
 * every event carries the same envelope, and a producer and consumer that
 * disagreed on a header name would fail silently (the header would just be
 * absent). Payload shapes such as {@code TransactionCreatedEvent} stay
 * duplicated per service, as every other cross-service contract on this
 * platform is.
 *
 * <p>The correlation id travels under
 * {@link com.spendwise.common.tracing.CorrelationIdConstants#HEADER_NAME}, the
 * same name it has on HTTP, so it reads the same in every log and tool.
 */
public final class EventHeaders {

    /** Outbox row id: the stable identity of one event, for consumer-side deduplication. */
    public static final String OUTBOX_EVENT_ID = "outboxEventId";

    /** Simple class name of the payload, e.g. {@code TransactionCreatedEvent}. */
    public static final String EVENT_TYPE = "eventType";

    /** Kind of aggregate the event is about, e.g. {@code Transaction}. */
    public static final String AGGREGATE_TYPE = "aggregateType";

    private EventHeaders() {
    }
}
