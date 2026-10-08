package com.spendwise.common.messaging;

/**
 * Milestone 20 — the dead-letter topic naming rule, shared by the side that
 * creates the topic (transaction-service's OutboxPublisher provisions
 * {@code transaction-events.DLT} together with {@code transaction-events})
 * and the side that writes to it (every consumer's
 * {@code DeadLetterPublishingRecoverer}).
 *
 * <p>Defined here, explicitly, rather than left to Spring Kafka's default:
 * in spring-kafka 3.3 the recoverer's built-in destination is
 * {@code topic + "-dlt"} (its own Javadoc still says {@code ".DLT"}), while the
 * roadmap, the brokers' topic list and every operator runbook say
 * {@code .DLT}. With topic auto-creation disabled on the brokers, a mismatch
 * would not create a stray topic — every dead-letter publish would fail
 * instead, and the poison record would be retried forever.
 */
public final class DeadLetterTopics {

    public static final String SUFFIX = ".DLT";

    private DeadLetterTopics() {
    }

    public static String of(String topic) {
        return topic + SUFFIX;
    }
}
