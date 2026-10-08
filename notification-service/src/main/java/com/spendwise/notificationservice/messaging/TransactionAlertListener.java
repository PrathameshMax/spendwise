package com.spendwise.notificationservice.messaging;

import com.spendwise.common.messaging.EventHeaders;
import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.messaging.IdempotencyMetrics;
import com.spendwise.events.avro.TransactionCreatedEvent;
import com.spendwise.events.avro.TransactionReversedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Milestone 18 — notification-service's consumer of {@code transaction-events}:
 * turns each recorded transaction into a user-facing alert.
 *
 * <p><b>Its own consumer group.</b> {@code group.id} is this service's own
 * name ({@code spring.kafka.consumer.group-id: ${spring.application.name}},
 * config-repo/application.yml). analytics-service consumes the same topic
 * under {@code analytics-service}. Kafka tracks committed offsets per group,
 * so each group receives every record on the topic independently: this
 * service sending an alert neither consumes the record away from analytics,
 * nor waits for it. Inside one group the opposite holds — each partition is
 * owned by exactly one member — which is what the chaos lab exercises by
 * running four instances of this service against three partitions.
 *
 * <p><b>{@code idIsGroup = false}.</b> Spring Kafka uses a listener's
 * {@code id} as its {@code group.id} unless told otherwise; without this flag
 * the group would be {@code transaction-alerts}, not the configured one.
 *
 * <p><b>Idempotent (Milestone 19).</b> Kafka delivers at least once, and the
 * outbox publisher can publish an event twice, so this listener must expect
 * duplicates. It hands each event to {@link TransactionAlertService}, which
 * claims the event's identity ({@link EventIdentity}: the outbox event id) in
 * {@code processed_events} in the same transaction as recording the alert,
 * and skips the event if the claim already exists. A duplicate is still
 * acknowledged — it must not be retried — and is counted under
 * {@code spendwise.kafka.events.idempotency{outcome=duplicate}}. Dispatch is
 * still simulated (a log line, after commit).
 *
 * <p><b>Two event types, one listener (Milestone 21).</b> The topic now also
 * carries {@code TransactionReversedEvent}, the end of the budget-breach
 * saga. It is dispatched here, by the {@code eventType} header, rather than by
 * a second {@code @KafkaListener}: two listeners on one topic in one consumer
 * group would split the topic's partitions between them, and each would only
 * ever see some of either event type. Any other type is skipped, so a future
 * event cannot be mis-read as one of these.
 *
 * <p><b>Avro (Milestone 22).</b> Values arrive decoded as the generated
 * classes, and dispatch is a type switch on them (Java 21 pattern matching)
 * rather than on the {@code eventType} header and a JSON parse. A record that
 * is not valid Avro for a registered schema, or whose record type has no class
 * in this build, never reaches this method: it fails in deserialization and
 * goes straight to {@code transaction-events.DLT} (one attempt, no retries),
 * where it can be replayed once a consumer that knows it is deployed.
 *
 * <p>A payload that cannot be parsed is rethrown. The platform's error handler
 * (Milestone 20, spendwise-common) retries it 3 times, 1 s apart, then
 * publishes it to {@code transaction-events.DLT} and moves on to the next
 * record in the partition.
 */
@Component
public class TransactionAlertListener {


    private static final Logger log = LoggerFactory.getLogger(TransactionAlertListener.class);

    private final TransactionAlertService transactionAlertService;
    private final MeterRegistry meterRegistry;

    public TransactionAlertListener(TransactionAlertService transactionAlertService, MeterRegistry meterRegistry) {
        this.transactionAlertService = transactionAlertService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = TransactionAlertService.CONSUMER, idIsGroup = false,
            topics = "${spendwise.kafka.topics.transaction-events}")
    public void onTransactionEvent(ConsumerRecord<String, Object> record) {
        EventIdentity eventIdentity = EventIdentity.of(record);
        boolean firstDelivery;
        switch (record.value()) {
            case TransactionCreatedEvent created ->
                    firstDelivery = transactionAlertService.recordAlert(eventIdentity, created);
            case TransactionReversedEvent reversed ->
                    firstDelivery = transactionAlertService.recordReversalAlert(eventIdentity, reversed);
            case null, default -> {
                log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                        header(record, EventHeaders.EVENT_TYPE), record.topic(), record.partition(), record.offset());
                return;
            }
        }
        IdempotencyMetrics.record(meterRegistry, TransactionAlertService.CONSUMER, firstDelivery);
        log.debug("{} {} from partition {} offset {}: {}", record.value().getClass().getSimpleName(), eventIdentity,
                record.partition(), record.offset(), firstDelivery ? "processed" : "duplicate, skipped");
    }

    private static String header(ConsumerRecord<String, Object> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
