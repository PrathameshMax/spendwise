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
 * <p>A payload that cannot be parsed is rethrown. The platform's error handler
 * (Milestone 20, spendwise-common) retries it 3 times, 1 s apart, then
 * publishes it to {@code transaction-events.DLT} and moves on to the next
 * record in the partition.
 */
@Component
public class TransactionAlertListener {

    static final String CREATED_EVENT_TYPE = "TransactionCreatedEvent";
    static final String REVERSED_EVENT_TYPE = "TransactionReversedEvent";

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
        EventIdentity eventIdentity = EventIdentity.of(record);
        boolean firstDelivery;
        if (CREATED_EVENT_TYPE.equals(eventType)) {
            firstDelivery = transactionAlertService.recordAlert(eventIdentity,
                    parse(record, TransactionCreatedEvent.class));
        } else if (REVERSED_EVENT_TYPE.equals(eventType)) {
            firstDelivery = transactionAlertService.recordReversalAlert(eventIdentity,
                    parse(record, TransactionReversedEvent.class));
        } else {
            log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                    eventType, record.topic(), record.partition(), record.offset());
            return;
        }
        IdempotencyMetrics.record(meterRegistry, TransactionAlertService.CONSUMER, firstDelivery);
        log.debug("{} {} from partition {} offset {}: {}", eventType, eventIdentity, record.partition(),
                record.offset(), firstDelivery ? "processed" : "duplicate, skipped");
    }

    private <T> T parse(ConsumerRecord<String, String> record, Class<T> type) {
        try {
            return objectMapper.readValue(record.value(), type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unparseable %s at %s-%d@%d"
                    .formatted(type.getSimpleName(), record.topic(), record.partition(), record.offset()), ex);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
