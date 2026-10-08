package com.spendwise.budgetservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.messaging.DeadLetterTopics;
import com.spendwise.common.messaging.EventHeaders;
import com.spendwise.common.tracing.CorrelationIdConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Milestone 21 — publishes budget-service's saga decisions to
 * {@code budget-events}, and blocks until the broker has acknowledged them.
 *
 * <p><b>Why no outbox here.</b> transaction-service needs an outbox because
 * its trigger is an HTTP request: once the response is sent, nothing would
 * ever retry a publish that failed after the commit. Here the trigger is a
 * Kafka record, and the record itself is the retry mechanism. The publish
 * happens inside {@code BudgetSpendService}'s transaction, before it
 * commits, and failure is an exception: the transaction (and its
 * {@code processed_events} claim) rolls back, the listener rethrows, and the
 * error handler redelivers the same {@code TransactionCreatedEvent}, which
 * re-runs the decision. The remaining inconsistency — the broker
 * acknowledged, then the commit failed — produces a second publish on
 * retry, never a lost one.
 *
 * <p><b>Deterministic event id.</b> That second publish must be recognisable
 * as the same event, so the id in the {@code outboxEventId} header is derived
 * from the decision's subject instead of generated: a name-based UUID of
 * {@code "TransactionRejectedEvent:" + transactionId}. Every publish of the
 * rejection of one transaction carries the same id, and transaction-service's
 * {@code processed_events} guard discards all but the first. The header keeps
 * the platform's name ({@link EventHeaders#OUTBOX_EVENT_ID}) so
 * {@code EventIdentity} reads it unchanged: it is the event's identity,
 * whichever mechanism produced it.
 *
 * <p><b>Topic provisioning is best-effort at startup, guaranteed at first
 * publish.</b> budget-service owns {@code budget-events} and
 * {@code budget-events.DLT} (brokers do not auto-create topics). It tries to
 * create both once the application is ready, so transaction-service's
 * listener finds the topic without waiting for the first rejection; a
 * failure there is only logged, so a Kafka outage never stops budget-service
 * from booting and serving its REST and gRPC APIs. Every publish re-checks
 * until one creation has succeeded, as transaction-service's
 * {@code OutboxPublisher} does.
 */
@Component
public class BudgetEventPublisher {

    static final String AGGREGATE_TYPE = "Transaction";

    private static final Logger log = LoggerFactory.getLogger(BudgetEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final ObjectMapper objectMapper;
    private final BudgetEventsProperties properties;
    private final NewTopic topic;
    private final NewTopic deadLetterTopic;

    private volatile boolean topicReady;

    public BudgetEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaAdmin kafkaAdmin,
                                ObjectMapper objectMapper, BudgetEventsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.topic = TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(properties.minInSyncReplicas()))
                .build();
        this.deadLetterTopic = TopicBuilder.name(DeadLetterTopics.of(properties.topic()))
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(properties.minInSyncReplicas()))
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(properties.deadLetterRetention().toMillis()))
                .build();
    }

    /**
     * The id every publish of this transaction's rejection carries.
     */
    public static UUID rejectionEventId(UUID transactionId) {
        return UUID.nameUUIDFromBytes(("TransactionRejectedEvent:" + transactionId)
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Publishes and waits for the acknowledgment.
     *
     * @throws IllegalStateException if the topic cannot be provisioned or the
     *                               broker does not acknowledge in time; the
     *                               caller's transaction must roll back
     */
    public UUID publishRejection(TransactionRejectedEvent event) {
        ensureTopic();
        UUID eventId = rejectionEventId(event.transactionId());
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topic(), event.transactionId().toString(), toJson(event));
        record.headers().add(EventHeaders.OUTBOX_EVENT_ID, utf8(eventId.toString()));
        record.headers().add(EventHeaders.EVENT_TYPE, utf8(TransactionRejectedEvent.class.getSimpleName()));
        record.headers().add(EventHeaders.AGGREGATE_TYPE, utf8(AGGREGATE_TYPE));
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (correlationId != null) {
            record.headers().add(CorrelationIdConstants.HEADER_NAME, utf8(correlationId));
        }
        try {
            SendResult<String, String> result = kafkaTemplate.send(record)
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.info("TransactionRejectedEvent {} for transaction {} acknowledged at {}-{}@{}", eventId,
                    event.transactionId(), result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            return eventId;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted awaiting acknowledgment of " + eventId, ex);
        } catch (ExecutionException | TimeoutException ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("TransactionRejectedEvent %s for transaction %s not acknowledged"
                    .formatted(eventId, event.transactionId()), cause);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provisionTopicsOnStartup() {
        try {
            ensureTopic();
        } catch (RuntimeException ex) {
            log.warn("Kafka cluster unreachable at startup, {} will be created on first publish: {}",
                    properties.topic(), ex.getMessage());
        }
    }

    private void ensureTopic() {
        if (topicReady) {
            return;
        }
        kafkaAdmin.createOrModifyTopics(topic, deadLetterTopic);
        topicReady = true;
        log.info("Topics {} and {} ready ({} partitions, replication factor {}, min.insync.replicas {})",
                properties.topic(), deadLetterTopic.name(), properties.partitions(),
                properties.replicationFactor(), properties.minInSyncReplicas());
    }

    private String toJson(TransactionRejectedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize " + event, ex);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
