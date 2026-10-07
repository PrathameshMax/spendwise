package com.spendwise.common.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Milestone 19 — the key a consumer deduplicates on: which event this record
 * carries, independent of where in the log it sits.
 *
 * <p><b>Why not the offset.</b> A record's topic/partition/offset identifies a
 * <em>position</em>, and the duplicates this platform actually produces do
 * not all share one. A consumer that crashes before committing its offset
 * re-reads the same position — same offset. But transaction-service's outbox
 * publisher is at-least-once: if it is acknowledged by the broker and dies
 * before committing {@code processed_at}, it publishes the same outbox row
 * again, which Kafka appends as a <em>new</em> record at a new offset. Only
 * the {@link EventHeaders#OUTBOX_EVENT_ID} header — the outbox row's primary
 * key, set once when the event was created — is the same across both kinds
 * of duplicate.
 *
 * <p><b>Fallback.</b> A record without that header (published by something
 * other than the outbox publisher) is keyed by
 * {@code topic-partition@offset}. That still discards consumer-side
 * redelivery of the same record, though not a producer-side resend; the
 * {@code source} tells the caller which guarantee it got.
 */
public final class EventIdentity {

    public enum Source { OUTBOX_EVENT_ID, RECORD_POSITION }

    private final String id;
    private final Source source;

    private EventIdentity(String id, Source source) {
        this.id = id;
        this.source = source;
    }

    public static EventIdentity of(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(EventHeaders.OUTBOX_EVENT_ID);
        if (header != null && header.value() != null && header.value().length > 0) {
            return new EventIdentity(new String(header.value(), StandardCharsets.UTF_8), Source.OUTBOX_EVENT_ID);
        }
        return new EventIdentity(record.topic() + "-" + record.partition() + "@" + record.offset(),
                Source.RECORD_POSITION);
    }

    public String id() {
        return id;
    }

    public Source source() {
        return source;
    }

    @Override
    public String toString() {
        return id + " (" + source + ")";
    }
}
