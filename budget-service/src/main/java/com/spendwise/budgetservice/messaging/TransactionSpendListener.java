package com.spendwise.budgetservice.messaging;

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
 * Milestone 21 — budget-service's consumer of {@code transaction-events}, in
 * its own consumer group ({@code budget-service}, from
 * {@code ${spring.application.name}}), independent of notification-service's
 * and analytics-service's groups on the same topic.
 *
 * <p>Only {@code TransactionCreatedEvent} is handled. The same topic also
 * carries {@code TransactionReversedEvent} (Milestone 21): budget-service
 * skips it, because the only reversals on this platform are the ones it
 * caused itself, for expenses it never added to spend — there is nothing to
 * take back.
 *
 * <p>Each event goes to {@link BudgetSpendService#apply}, and the outcome is
 * counted twice: once under the shared
 * {@code spendwise.kafka.events.idempotency} (processed/duplicate, Milestone
 * 19), and once under {@code spendwise.saga.budget.decisions{outcome}}
 * (applied, rejected, no_budget, not_expense, unattributable, duplicate) — the
 * saga's own measure of how often HARD caps actually trigger compensation.
 *
 * <p>Failures (an unparseable payload, an unacknowledged rejection publish)
 * are rethrown to the platform error handler: 3 retries, 1 s apart, then
 * {@code transaction-events.DLT} (Milestone 20).
 */
@Component
public class TransactionSpendListener {

    static final String HANDLED_EVENT_TYPE = "TransactionCreatedEvent";
    static final String DECISIONS_METRIC = "spendwise.saga.budget.decisions";

    private static final Logger log = LoggerFactory.getLogger(TransactionSpendListener.class);

    private final ObjectMapper objectMapper;
    private final BudgetSpendService budgetSpendService;
    private final MeterRegistry meterRegistry;

    public TransactionSpendListener(ObjectMapper objectMapper, BudgetSpendService budgetSpendService,
                                    MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.budgetSpendService = budgetSpendService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(id = BudgetSpendService.CONSUMER, idIsGroup = false,
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
        BudgetDecision decision = budgetSpendService.apply(eventIdentity, event);
        IdempotencyMetrics.record(meterRegistry, BudgetSpendService.CONSUMER, decision != BudgetDecision.DUPLICATE);
        Counter.builder(DECISIONS_METRIC)
                .description("Budget-breach saga decisions, by outcome")
                .tag("outcome", decision.tag())
                .register(meterRegistry)
                .increment();
        log.debug("Event {} from partition {} offset {}: {}", eventIdentity, record.partition(), record.offset(),
                decision.tag());
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
