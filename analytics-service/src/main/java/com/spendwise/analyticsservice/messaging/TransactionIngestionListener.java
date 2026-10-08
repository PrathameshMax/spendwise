package com.spendwise.analyticsservice.messaging;

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
 * <p>Any failure here — an unparseable payload, a timed-out ingest — is
 * rethrown to the platform's error handler (Milestone 20, spendwise-common):
 * retried 3 times, 1 s apart, then published to {@code transaction-events.DLT},
 * after which the partition moves on.
 */
@Component
public class TransactionIngestionListener {

    static final String HANDLED_EVENT_TYPE = "TransactionCreatedEvent";

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestionListener.class);

    private static final Duration INGEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final TransactionIngestionService transactionIngestionService;
    private final MeterRegistry meterRegistry;

    public TransactionIngestionListener(ObjectMapper objectMapper,
                                        TransactionIngestionService transactionIngestionService,
                                        MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.transactionIngestionService = transactionIngestionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = TransactionIngestionService.CONSUMER, idIsGroup = false,
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
        Boolean firstDelivery = transactionIngestionService.ingest(eventIdentity, event).block(INGEST_TIMEOUT);
        boolean processed = Boolean.TRUE.equals(firstDelivery);
        IdempotencyMetrics.record(meterRegistry, TransactionIngestionService.CONSUMER, processed);
        if (processed) {
            log.info("Read-side ingest: user {}, month {}, category {}, {} {} {} (base amount {}), event {}, partition {} offset {}",
                    event.userId(), YearMonth.from(event.transactionDate()), event.categoryId(), event.type(),
                    event.amount(), event.currency(), event.baseCurrencyAmount(), eventIdentity,
                    record.partition(), record.offset());
        } else {
            log.info("Duplicate delivery of event {} for transaction {} discarded (partition {} offset {})",
                    eventIdentity, event.transactionId(), record.partition(), record.offset());
        }
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
