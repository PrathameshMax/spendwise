package com.spendwise.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.messaging.EventHeaders;
import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.messaging.IdempotencyMetrics;
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
 * still simulated (a log line, after commit). Records of any other event type on
 * this topic are skipped, so a future event (Milestone 21's
 * {@code TransactionReversedEvent}) cannot be mis-read as a new transaction.
 *
 * <p>A payload that cannot be parsed is rethrown: Spring Kafka's default error
 * handler retries it and then logs and skips it. Routing such records to a
 * dead-letter topic instead is Milestone 20.
 */
@Component
public class TransactionAlertListener {

    static final String HANDLED_EVENT_TYPE = "TransactionCreatedEvent";

    private static final Logger log = LoggerFactory.getLogger(TransactionAlertListener.class);

    private final ObjectMapper objectMapper;
    private final TransactionAlertService transactionAlertService;
    private final MeterRegistry meterRegistry;

    public TransactionAlertListener(ObjectMapper objectMapper, TransactionAlertService transactionAlertService,
                                    MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.transactionAlertService = transactionAlertService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = TransactionAlertService.CONSUMER, idIsGroup = false,
            topics = "${spendwise.kafka.topics.transaction-events}")
    public void onTransactionEvent(ConsumerRecord<String, String> record) {
        String eventType = header(record, EventHeaders.EVENT_TYPE);
        if (!HANDLED_EVENT_TYPE.equals(eventType)) {
            log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                    eventType, record.topic(), record.partition(), record.offset());
            return;
        }
        TransactionCreatedEvent event = parse(record);
        EventIdentity eventIdentity = EventIdentity.of(record);
        boolean firstDelivery = transactionAlertService.recordAlert(eventIdentity, event);
        IdempotencyMetrics.record(meterRegistry, TransactionAlertService.CONSUMER, firstDelivery);
        log.debug("Event {} from partition {} offset {}: {}", eventIdentity, record.partition(), record.offset(),
                firstDelivery ? "processed" : "duplicate, skipped");
    }

    private TransactionCreatedEvent parse(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), TransactionCreatedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unparseable TransactionCreatedEvent at %s-%d@%d"
                    .formatted(record.topic(), record.partition(), record.offset()), ex);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
