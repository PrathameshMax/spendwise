package com.spendwise.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.messaging.EventHeaders;
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
 * <p><b>Dispatch is simulated.</b> Delivery channels (email/SMS/push,
 * WebSocket) and the idempotency store that guards them come with Milestones
 * 19 onward; this milestone's job is the consumption itself, so the alert is
 * written to the log, carrying the request's correlation id via
 * {@code CorrelationIdRecordInterceptor}. Records of any other event type on
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

    public TransactionAlertListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = "transaction-alerts", idIsGroup = false,
            topics = "${spendwise.kafka.topics.transaction-events}")
    public void onTransactionEvent(ConsumerRecord<String, String> record) {
        String eventType = header(record, EventHeaders.EVENT_TYPE);
        if (!HANDLED_EVENT_TYPE.equals(eventType)) {
            log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                    eventType, record.topic(), record.partition(), record.offset());
            return;
        }
        TransactionCreatedEvent event = parse(record);
        log.info("[SIMULATED PUSH] to user {}: {} of {} {} recorded for {} (transaction {}, partition {}, offset {})",
                event.userId(), event.type(), event.amount(), event.currency(), event.transactionDate(),
                event.transactionId(), record.partition(), record.offset());
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
