package com.spendwise.common.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/**
 * Milestone 20 — what the error handler does with a record once its retries
 * are exhausted: publish it to {@code <topic>.DLT} through
 * {@link DeadLetterPublishingRecoverer}, then log and count it.
 *
 * <p>The delegate writes the original key, value and headers (correlation id
 * and {@code outboxEventId} included) to the same partition number of the
 * dead-letter topic, and adds {@code kafka_dlt-*} headers recording where the
 * record came from (original topic, partition, offset, timestamp, consumer
 * group) and why it failed (exception class, message, stack trace) — what an
 * operator needs to diagnose it and, once fixed, replay it.
 *
 * <p>Counted as {@code spendwise.kafka.records.deadlettered{group, topic}},
 * only after the publish succeeded. If the publish itself fails (the
 * dead-letter topic is unreachable), the exception propagates, the error
 * handler treats the record as not recovered, and it is redelivered. A record
 * is never acknowledged without having been either processed or safely
 * parked.
 *
 * <p>Each consumer group dead-letters independently: a record that is poison
 * for notification-service and analytics-service lands in the DLT twice, one
 * copy per group, told apart by the {@code kafka_dlt-original-consumer-group}
 * header. That is correct — each group failed it, and each may later replay
 * its own copy.
 */
public class MeteredDeadLetterRecoverer implements ConsumerAwareRecordRecoverer {

    public static final String DEAD_LETTERED_METRIC = "spendwise.kafka.records.deadlettered";

    private static final Logger log = LoggerFactory.getLogger(MeteredDeadLetterRecoverer.class);

    private final DeadLetterPublishingRecoverer delegate;
    private final MeterRegistry meterRegistry;

    public MeteredDeadLetterRecoverer(DeadLetterPublishingRecoverer delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception exception) {
        delegate.accept(record, consumer, exception);
        String group = groupId(consumer);
        log.warn("Retries exhausted for {}-{}@{} (group {}); record published to {}: {}",
                record.topic(), record.partition(), record.offset(), group,
                DeadLetterTopics.of(record.topic()), rootCauseMessage(exception));
        Counter.builder(DEAD_LETTERED_METRIC)
                .description("Records published to a dead-letter topic after exhausting retries")
                .tag("group", group)
                .tag("topic", record.topic())
                .register(meterRegistry)
                .increment();
    }

    private static String groupId(Consumer<?, ?> consumer) {
        if (consumer == null) {
            return "unknown";
        }
        try {
            return consumer.groupMetadata().groupId();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private static String rootCauseMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
