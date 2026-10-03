package com.spendwise.transactionservice.outbox;

import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.transactionservice.domain.OutboxEvent;
import com.spendwise.transactionservice.domain.OutboxEventRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Milestone 17 — the relay half of the Transactional Outbox: polls
 * {@code outbox_events} for unprocessed rows and publishes each one to the
 * multi-partition {@code transaction-events} topic, setting
 * {@code processed_at} only after the broker acknowledges the record.
 *
 * <p><b>Claim → send → acknowledge → mark, inside one local transaction.</b>
 * Each poll runs in a {@link TransactionTemplate} transaction: rows are
 * claimed with {@code FOR UPDATE SKIP LOCKED}
 * ({@link OutboxEventRepository#lockNextUnprocessedBatch}), sent one at a
 * time, and each {@code send(...).get(...)} blocks until the broker has
 * acknowledged under {@code acks=all} (every in-sync replica, at least
 * {@code min.insync.replicas = 2} of 3, has the record). Only then is the row
 * marked processed. Blocking is correct here, not a smell: this is a
 * dedicated scheduler thread, never a request thread, and ordering matters
 * more than throughput at this stage.
 *
 * <p><b>Failure stops the batch; it never skips a row.</b> On the first send
 * that fails or times out, the loop stops: rows already acknowledged commit
 * as processed, the failed row and everything after it stay unprocessed and
 * are claimed again on the next poll, in the same {@code created_at} order.
 * Continuing past a failure would publish later rows ahead of an earlier one
 * for the same aggregate.
 *
 * <p><b>At-least-once, by construction.</b> If the broker acknowledges but
 * the process dies before the transaction commits {@code processed_at}, the
 * row is still unprocessed after restart and is published again. There is no
 * window in which an event is lost; there is a window in which one is
 * duplicated. The {@code outboxEventId} header exists so Milestone 19's
 * consumer-side idempotency layer can discard exactly those duplicates.
 *
 * <p><b>Ordering.</b> The record key is {@code aggregateId} (the
 * transaction's id), so every event about one aggregate lands on the same
 * partition and is consumed in order, while different aggregates spread
 * across all three partitions — the parallelism Milestone 18's consumer
 * groups scale against.
 *
 * <p><b>Topic provisioning is lazy, not a startup dependency.</b> The topic
 * is created (or its partition count raised) on the first poll that can
 * reach the cluster, not at application start. transaction-service must keep
 * accepting writes while Kafka is down — that is the outbox pattern's entire
 * promise — so Kafka cannot be a boot-time hard dependency; rows simply
 * accumulate and drain once the cluster is reachable.
 */
@Component
@ConditionalOnProperty(prefix = "spendwise.outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    static final String HEADER_OUTBOX_EVENT_ID = "outboxEventId";
    static final String HEADER_EVENT_TYPE = "eventType";
    static final String HEADER_AGGREGATE_TYPE = "aggregateType";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final TransactionTemplate transactionTemplate;
    private final OutboxPublisherProperties properties;
    private final NewTopic topic;

    private volatile boolean topicReady;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           KafkaAdmin kafkaAdmin,
                           TransactionTemplate transactionTemplate,
                           OutboxPublisherProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.topic = TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(properties.minInSyncReplicas()))
                .build();
    }

    @Scheduled(fixedDelayString = "${spendwise.outbox.publisher.poll-interval-ms:1000}",
            initialDelayString = "${spendwise.outbox.publisher.initial-delay-ms:5000}")
    public void publishPendingEvents() {
        if (!ensureTopic()) {
            return;
        }
        Integer published = transactionTemplate.execute(status -> publishBatch());
        if (published != null && published > 0) {
            log.info("Outbox relay published {} event(s) to {}", published, properties.topic());
        }
    }

    private boolean ensureTopic() {
        if (topicReady) {
            return true;
        }
        try {
            kafkaAdmin.createOrModifyTopics(topic);
            topicReady = true;
            log.info("Topic {} ready ({} partitions, replication factor {}, min.insync.replicas {})",
                    properties.topic(), properties.partitions(), properties.replicationFactor(),
                    properties.minInSyncReplicas());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Kafka cluster unreachable, outbox rows will accumulate until it recovers: {}",
                    ex.getMessage());
            return false;
        }
    }

    private int publishBatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextUnprocessedBatch(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            if (!publishOne(event)) {
                break;
            }
            event.markProcessed(Instant.now());
            published++;
        }
        return published;
    }

    private boolean publishOne(OutboxEvent event) {
        ProducerRecord<String, String> record = toRecord(event);
        String correlationId = event.getCorrelationId();
        if (correlationId != null) {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        }
        try {
            SendResult<String, String> result = kafkaTemplate.send(record)
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Outbox event {} acknowledged at {}-{}@{}", event.getId(),
                    result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while awaiting acknowledgment for outbox event {}", event.getId());
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            log.warn("Outbox event {} not acknowledged, will retry next poll: {}",
                    event.getId(), cause.toString());
            return false;
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topic(), event.getAggregateId().toString(), event.getPayload());
        record.headers().add(HEADER_OUTBOX_EVENT_ID, utf8(event.getId().toString()));
        record.headers().add(HEADER_EVENT_TYPE, utf8(event.getEventType()));
        record.headers().add(HEADER_AGGREGATE_TYPE, utf8(event.getAggregateType()));
        if (event.getCorrelationId() != null) {
            record.headers().add(CorrelationIdConstants.HEADER_NAME, utf8(event.getCorrelationId()));
        }
        return record;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
