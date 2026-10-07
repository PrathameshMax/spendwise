package com.spendwise.common.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Milestone 19 — {@code spendwise.kafka.events.idempotency}, counted once
 * per delivery that reached an idempotency guard, tagged with the consumer
 * name and {@code outcome}:
 * <ul>
 *   <li>{@code processed} — first delivery of this event to this consumer; its
 *   side effects ran and committed.</li>
 *   <li>{@code duplicate} — the event had already been processed by this
 *   consumer; the delivery was acknowledged and discarded.</li>
 * </ul>
 * Read next to {@code spendwise.kafka.records.consumed} (every delivery
 * attempt, Milestone 18): deliveries minus {@code processed} is the
 * redelivery the guard absorbed. A delivery whose processing failed and
 * rolled back is counted by neither outcome here — its claim rolled back with
 * it, so the retry counts as the first.
 */
public final class IdempotencyMetrics {

    public static final String METRIC = "spendwise.kafka.events.idempotency";

    private IdempotencyMetrics() {
    }

    public static void record(MeterRegistry registry, String consumer, boolean firstDelivery) {
        Counter.builder(METRIC)
                .description("Events reaching an idempotency guard, by outcome")
                .tag("consumer", consumer)
                .tag("outcome", firstDelivery ? "processed" : "duplicate")
                .register(registry)
                .increment();
    }
}
