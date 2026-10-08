package com.spendwise.transactionservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.messaging.EventHeaders;
import com.spendwise.common.messaging.EventIdentity;
import com.spendwise.common.messaging.IdempotencyMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Milestone 21 — transaction-service's first inbound Kafka path: it consumes
 * {@code budget-events}, the topic budget-service publishes its saga
 * decisions to, in consumer group {@code transaction-service}.
 *
 * <p>This is choreography, not orchestration: no coordinator tells
 * transaction-service to reverse anything. budget-service publishes a fact
 * ("this transaction breached a HARD cap"), and transaction-service reacts to
 * it on its own terms through {@link TransactionReversalService}. Neither
 * service calls the other, and either can be down while the other keeps
 * working; the saga simply completes later (Q62, Q63).
 *
 * <p>Outcomes are counted under the shared
 * {@code spendwise.kafka.events.idempotency} and under
 * {@code spendwise.saga.reversals{outcome}} (reversed, already_reversed,
 * unknown_transaction, duplicate). Failures go to the platform error handler:
 * 3 retries, then {@code budget-events.DLT}, which budget-service creates
 * together with {@code budget-events}.
 */
@Component
public class TransactionRejectionListener {

    static final String HANDLED_EVENT_TYPE = "TransactionRejectedEvent";
    static final String REVERSALS_METRIC = "spendwise.saga.reversals";

    private static final Logger log = LoggerFactory.getLogger(TransactionRejectionListener.class);

    private final ObjectMapper objectMapper;
    private final TransactionReversalService transactionReversalService;
    private final MeterRegistry meterRegistry;

    public TransactionRejectionListener(ObjectMapper objectMapper,
                                        TransactionReversalService transactionReversalService,
                                        MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.transactionReversalService = transactionReversalService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = TransactionReversalService.CONSUMER, idIsGroup = false,
            topics = "${spendwise.kafka.topics.budget-events}")
    public void onBudgetEvent(ConsumerRecord<String, String> record) {
        String eventType = header(record, EventHeaders.EVENT_TYPE);
        if (!HANDLED_EVENT_TYPE.equals(eventType)) {
            log.debug("Skipping {} at {}-{}@{}: not handled by this listener",
                    eventType, record.topic(), record.partition(), record.offset());
            return;
        }
        TransactionRejectedEvent event = parse(record);
        EventIdentity eventIdentity = EventIdentity.of(record);
        ReversalOutcome outcome = transactionReversalService.reverse(eventIdentity, event);
        IdempotencyMetrics.record(meterRegistry, TransactionReversalService.CONSUMER,
                outcome != ReversalOutcome.DUPLICATE);
        Counter.builder(REVERSALS_METRIC)
                .description("Compensating reversals of the budget-breach saga, by outcome")
                .tag("outcome", outcome.tag())
                .register(meterRegistry)
                .increment();
        log.debug("Event {} from partition {} offset {}: {}", eventIdentity, record.partition(), record.offset(),
                outcome.tag());
    }

    private TransactionRejectedEvent parse(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), TransactionRejectedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unparseable TransactionRejectedEvent at %s-%d@%d"
                    .formatted(record.topic(), record.partition(), record.offset()), ex);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
