package com.spendwise.common.messaging;

import com.spendwise.common.tracing.CorrelationIdConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Milestone 18 — the consumer-side counterpart of {@code CorrelationIdFilter}:
 * puts the record's {@code X-Correlation-ID} header into the SLF4J MDC for
 * exactly the duration of one record's processing, and counts every record
 * processed.
 *
 * <p><b>Correlation id.</b> The producer (transaction-service's
 * OutboxPublisher) copies the originating HTTP request's correlation id onto
 * each record. Without this interceptor, a consumer's log lines would carry
 * the trace id of the Kafka span but not the id the client, the Gateway and
 * transaction-service all logged — so one request could not be followed into
 * its asynchronous consequences by a single search. MDC is safe here, unlike
 * on a WebFlux request path: a listener container runs each record start to
 * finish on its own dedicated consumer thread, and Spring Kafka calls
 * {@link #afterRecord} on that same thread after the listener and the error
 * handler are done, so the value is always removed before the thread touches
 * another record. That holds in analytics-service too, which is WebFlux for
 * HTTP but whose listener runs on a plain consumer thread, not a Netty event
 * loop.
 *
 * <p><b>Metric.</b> {@code spendwise.kafka.records.consumed}, tagged with the
 * consumer group, topic, partition, event type and outcome. Tagging by
 * partition is what makes consumer-group mechanics observable per instance
 * through {@code /actuator/metrics}: each instance only ever counts records
 * from the partitions it was assigned. Counted on {@link #success}/{@link #failure},
 * i.e. once per delivery attempt — a redelivered record is counted again,
 * which is accurate for "work done" and is exactly the duplication Milestone 19's
 * idempotency layer will make visible.
 *
 * <p>Registered automatically on every listener container by Spring Boot,
 * which applies a single {@code RecordInterceptor<Object, Object>} bean to the
 * auto-configured container factory.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    public static final String CONSUMED_METRIC = "spendwise.kafka.records.consumed";

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public CorrelationIdRecordInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        String correlationId = headerValue(record, CorrelationIdConstants.HEADER_NAME);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        }
        return record;
    }

    @Override
    public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        count(record, consumer, "success");
    }

    @Override
    public void failure(ConsumerRecord<Object, Object> record, Exception exception,
                        Consumer<Object, Object> consumer) {
        count(record, consumer, "failure");
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    private void count(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer, String outcome) {
        String eventType = headerValue(record, EventHeaders.EVENT_TYPE);
        Counter.builder(CONSUMED_METRIC)
                .description("Kafka records processed by this instance's listeners")
                .tag("group", groupId(consumer))
                .tag("topic", record.topic())
                .tag("partition", String.valueOf(record.partition()))
                .tag("event.type", eventType != null ? eventType : UNKNOWN)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private static String groupId(Consumer<Object, Object> consumer) {
        try {
            return consumer.groupMetadata().groupId();
        } catch (RuntimeException ex) {
            // groupMetadata() throws for a consumer with no group.id; every
            // listener here has one, but a metric must never fail a record.
            return UNKNOWN;
        }
    }

    static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
