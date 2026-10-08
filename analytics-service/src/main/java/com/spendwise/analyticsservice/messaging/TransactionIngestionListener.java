package com.spendwise.analyticsservice.messaging;

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
import java.time.Duration;
import java.time.YearMonth;

/**
 * Milestone 18 — analytics-service's consumer of {@code transaction-events}:
 * the entry point of the read side (CQRS). It consumes the same records as
 * notification-service's {@code TransactionAlertListener}, for a different
 * purpose — not to tell a user something now, but to feed the aggregates
 * dashboards are later served from — and in its own consumer group
 * ({@code analytics-service}, from {@code ${spring.application.name}}), so the
 * two services progress through the topic completely independently. Either
 * one can be down, slow, or replaying from offset zero (Milestone 23) without
 * the other noticing.
 *
 * <p>Scoped to consumption, as this milestone is: each event is parsed into
 * this service's own view of it and logged with the dimensions the read
 * models will key on (user, month, category). Building and storing the
 * monthly-trend and category-breakdown projections from these events is
 * Milestone 23. {@code auto-offset-reset: earliest} means this group's first
 * start reads the topic from the beginning, so events published before it
 * existed (Milestone 17's) are not missed — the property Milestone 23's
 * replay depends on.
 *
 * <p>This service is WebFlux for HTTP, but this method is ordinary blocking
 * code: the listener container runs it on its own consumer thread, never on
 * a Netty event-loop thread, so it cannot stall request handling.
 *
 * <p><b>Idempotent (Milestone 19).</b> Each event goes through
 * {@link TransactionIngestionService}, which claims its identity in
 * {@code processed_events} inside an R2DBC transaction and skips it if
 * already claimed. That pipeline is reactive, and this listener waits for it
 * with {@code block(timeout)}: blocking is what the listener contract
 * requires — the offset must not be committed until the claim has — and it is
 * legal here because a Kafka consumer thread is not one of Reactor's
 * non-blocking threads. The timeout turns a hung database into a listener
 * failure (retried by the error handler) instead of a consumer stalled
 * forever, and stays well under {@code max.poll.interval.ms} (5 minutes).
 *
 * <p><b>Reversals (Milestone 21).</b> {@code TransactionReversedEvent} arrives
 * on the same topic, on the same partition as and after the
 * {@code TransactionCreatedEvent} it cancels (both are keyed by the original
 * transaction id). It is handled by this same listener, dispatched on the
 * {@code eventType} header — a second listener in this group would take half
 * the partitions — and logged as a read-side retraction; subtracting it from
 * the projections is Milestone 23.
 *
 * <p><b>Avro (Milestone 22).</b> Values arrive decoded as the generated
 * classes from spendwise-events, and the listener switches on their type. A
 * record that is not valid Avro is dead-lettered before it reaches this method.
 *
 * <p>Any failure here — an unparseable payload, a timed-out ingest — is
 * rethrown to the platform's error handler (Milestone 20, spendwise-common):
 * retried 3 times, 1 s apart, then published to {@code transaction-events.DLT},
 * after which the partition moves on.
 */
@Component
public class TransactionIngestionListener {


    private static final Logger log = LoggerFactory.getLogger(TransactionIngestionListener.class);

    private static final Duration INGEST_TIMEOUT = Duration.ofSeconds(10);

    private final TransactionIngestionService transactionIngestionService;
    private final MeterRegistry meterRegistry;

    public TransactionIngestionListener(TransactionIngestionService transactionIngestionService,
                                        MeterRegistry meterRegistry) {
        this.transactionIngestionService = transactionIngestionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = TransactionIngestionService.CONSUMER, idIsGroup = false,
            topics = "${spendwise.kafka.topics.transaction-events}")
    public void onTransactionEvent(ConsumerRecord<String, Object> record) {
        switch (record.value()) {
            case TransactionCreatedEvent created -> onCreated(record, created);
            case TransactionReversedEvent reversed -> onReversed(record, reversed);
            case null, default -> log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                    header(record, EventHeaders.EVENT_TYPE), record.topic(), record.partition(), record.offset());
        }
    }

    private void onCreated(ConsumerRecord<String, Object> record, TransactionCreatedEvent event) {
        EventIdentity eventIdentity = EventIdentity.of(record);
        Boolean firstDelivery = transactionIngestionService.ingest(eventIdentity, event).block(INGEST_TIMEOUT);
        boolean processed = Boolean.TRUE.equals(firstDelivery);
        IdempotencyMetrics.record(meterRegistry, TransactionIngestionService.CONSUMER, processed);
        if (processed) {
            log.info("Read-side ingest: user {}, month {}, category {}, {} {} {} (base amount {}), event {}, partition {} offset {}",
                    event.getUserId(), YearMonth.from(event.getTransactionDate()), event.getCategoryId(),
                    event.getType(), event.getAmount(), event.getCurrency(), event.getBaseCurrencyAmount(),
                    eventIdentity, record.partition(), record.offset());
        } else {
            log.info("Duplicate delivery of event {} for transaction {} discarded (partition {} offset {})",
                    eventIdentity, event.getTransactionId(), record.partition(), record.offset());
        }
    }

    private void onReversed(ConsumerRecord<String, Object> record, TransactionReversedEvent event) {
        EventIdentity eventIdentity = EventIdentity.of(record);
        Boolean firstDelivery = transactionIngestionService.retract(eventIdentity, event).block(INGEST_TIMEOUT);
        boolean processed = Boolean.TRUE.equals(firstDelivery);
        IdempotencyMetrics.record(meterRegistry, TransactionIngestionService.CONSUMER, processed);
        if (processed) {
            log.info("Read-side retraction: transaction {} reversed by {} — user {}, month {}, category {}, {} {} {} (base amount {}) removed; reason: {}; event {}, partition {} offset {}",
                    event.getTransactionId(), event.getReversalTransactionId(), event.getUserId(),
                    YearMonth.from(event.getTransactionDate()), event.getCategoryId(), event.getType(),
                    event.getAmount(), event.getCurrency(), event.getBaseCurrencyAmount(), event.getReason(),
                    eventIdentity, record.partition(), record.offset());
        } else {
            log.info("Duplicate delivery of event {} for transaction {} discarded (partition {} offset {})",
                    eventIdentity, event.getTransactionId(), record.partition(), record.offset());
        }
    }

    private static String header(ConsumerRecord<String, Object> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
