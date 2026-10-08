package com.spendwise.transactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.transactionservice.domain.OutboxEvent;
import com.spendwise.transactionservice.domain.OutboxEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Milestone 21 — the outbox write, extracted from {@code TransactionService}
 * (Milestones 16-17) now that a second business operation publishes through
 * the outbox: {@code TransactionReversalService}'s
 * {@code TransactionReversedEvent}. The rules are unchanged and now apply to
 * both:
 *
 * <ul>
 *   <li><b>Same transaction as the business write.</b> {@code MANDATORY}
 *   propagation makes recording an event outside the transaction that
 *   changes the ledger an immediate error — the dual write the outbox exists
 *   to prevent.</li>
 *   <li><b>Aggregate = Transaction.</b> {@code aggregateId} becomes the record
 *   key, so every event about one ledger entry (created, then reversed) lands
 *   on the same partition of {@code transaction-events}, in order.</li>
 *   <li><b>Correlation id from the MDC.</b> On an HTTP request thread,
 *   {@code CorrelationIdFilter} put it there; on a Kafka consumer thread,
 *   {@code CorrelationIdRecordInterceptor} did, from the incoming record's
 *   header. Either way the id travels on: one correlation id follows the
 *   whole saga, from the original POST through budget-service and back.</li>
 * </ul>
 *
 * <p>A serialization failure is a programming error (every payload is made
 * of Jackson-trivial types), so it is wrapped unchecked and rolls back the
 * transaction rather than being reported as a business outcome.
 */
@Component
public class OutboxEventRecorder {

    static final String AGGREGATE_TYPE_TRANSACTION = "Transaction";
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventRecorder(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(UUID transactionId, Object event) {
        String eventType = event.getClass().getSimpleName();
        try {
            return outboxEventRepository.save(new OutboxEvent(
                    AGGREGATE_TYPE_TRANSACTION,
                    transactionId,
                    eventType,
                    objectMapper.writeValueAsString(event),
                    persistableCorrelationId()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize " + eventType + " for transaction " + transactionId, ex);
        }
    }

    /**
     * The correlation id is client-supplied (the filters only replace a
     * missing or blank header), while {@code outbox_events.correlation_id} is
     * {@code VARCHAR(64)}. An over-long value is dropped rather than truncated
     * — a truncated id would no longer match anything — so a malformed header
     * can never fail the outbox insert and, with it, roll back the transaction.
     */
    private static String persistableCorrelationId() {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        return correlationId != null && correlationId.length() <= MAX_CORRELATION_ID_LENGTH ? correlationId : null;
    }
}
