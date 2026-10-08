package com.spendwise.common.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milestone 18 — consumer-side cross-cutting behaviour for every service that
 * has a {@code @KafkaListener}, in one place: correlation-id bridging and the
 * consumed-records metric ({@link CorrelationIdRecordInterceptor}), rebalance
 * logging ({@link PartitionAssignmentLoggingListener}), and the
 * {@code kafkaassignments} actuator endpoint ({@link KafkaAssignmentsEndpoint}).
 *
 * <p>Same pattern as {@code SpendwiseFeignTracingAutoConfiguration}: it only
 * activates when spring-kafka is on the classpath, which this module declares
 * as {@code optional} so services without Kafka never inherit it. Ordered
 * before {@link KafkaAutoConfiguration} so these bean definitions exist when
 * Boot's {@code @ConditionalOnMissingBean}-guarded listener container factory
 * is defined; Boot then applies them as the unique {@code RecordInterceptor}
 * and {@code ConsumerAwareRebalanceListener} beans of the context.
 * Since Milestone 21 every Kafka service has a listener — transaction-service
 * on {@code budget-events}, budget-service on {@code transaction-events} — so
 * these beans are live in all four; the assignments endpoint is exposed only
 * where a service's config lists it.
 *
 * <p>The meter registry is resolved through {@link ObjectProvider} rather
 * than guarded with {@code @ConditionalOnBean}: a bean condition in an
 * auto-configuration depends on the order other auto-configurations were
 * processed in, and could silently drop the interceptor. Every service with a
 * listener has Actuator, so the application's registry is always there; the
 * global registry is only a defensive fallback.
 *
 * <p>Milestone 20 adds the platform's single {@link CommonErrorHandler}:
 * bounded retries, then dead-lettering ({@link #deadLetterErrorHandler}).
 * Boot applies it to the auto-configured listener container factory as the
 * unique {@code CommonErrorHandler} bean, the same way as the interceptor.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass(RecordInterceptor.class)
@EnableConfigurationProperties(KafkaErrorHandlingProperties.class)
public class SpendwiseKafkaConsumerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    public RecordInterceptor<Object, Object> correlationIdRecordInterceptor(
            ObjectProvider<MeterRegistry> meterRegistry) {
        return new CorrelationIdRecordInterceptor(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    @Bean
    @ConditionalOnMissingBean(ConsumerAwareRebalanceListener.class)
    public ConsumerAwareRebalanceListener partitionAssignmentLoggingListener() {
        return new PartitionAssignmentLoggingListener();
    }

    /**
     * Milestone 20 — what happens when a listener throws. Spring Kafka's
     * out-of-the-box handler already retries and then logs and skips a
     * failing record, so a poison pill never blocked a partition forever,
     * but the skipped record was gone: logged once, unrecoverable, its offset
     * committed past. This handler keeps the bounded retries and parks the
     * record instead of discarding it.
     *
     * <ul>
     *   <li><b>Bounded retries</b> — {@link FixedBackOff}: {@code maxRetries}
     *   further attempts {@code retryInterval} apart (default 3 x 1 s, so 4
     *   attempts). Retries are performed by seeking the partition back to the
     *   failed record, so records behind it in that partition wait — that is
     *   the cost a bound exists to cap.</li>
     *   <li><b>Then dead-letter</b> — {@link DeadLetterPublishingRecoverer} to
     *   {@code <topic>.DLT}, same partition number (both topics have 3), via
     *   {@link DeadLetterTopics} rather than the library's {@code -dlt}
     *   default. Once it is published the offset is committed and the
     *   partition moves on: one poison record costs about 3 s of delay, not
     *   a stalled partition.</li>
     *   <li><b>No retries for exceptions that can never succeed</b> — the
     *   handler's built-in list (conversion failures, {@code ClassCastException},
     *   ...) goes straight to the DLT. Since Milestone 22, deserialization
     *   failures are decided by {@link DeserializationFailureBackOff}: a record
     *   that is not valid Avro for a known schema gets one attempt, then is
     *   parked; one that failed only because the Schema Registry was
     *   unreachable is retried until the registry is back. A payload
     *   our own code fails to parse ({@code IllegalArgumentException}) is
     *   deliberately left retryable here, so the bounded-retry path is the
     *   one exercised; Q61's answer covers when to classify it as fatal.</li>
     * </ul>
     *
     * <p><b>Two dead-letter templates, chosen by the value's type (Milestone
     * 22).</b> Values are Avro, so the record a listener received is either a
     * generated {@code SpecificRecord} or — when it could not be decoded at all
     * ({@code ErrorHandlingDeserializer}, config-repo/application.yml) — absent,
     * with its raw bytes inside the {@code DeserializationException}.
     * {@link DeadLetterPublishingRecoverer} publishes those raw bytes as the
     * value, and picks the first template whose key type matches it:
     * <ul>
     *   <li>{@code byte[]} — a copy of Boot's template with a
     *   {@link ByteArraySerializer}: the undecodable bytes are parked exactly as
     *   they arrived. Through the Avro serializer they would be re-wrapped as an
     *   Avro {@code bytes} value under a new schema — no longer the original
     *   record.</li>
     *   <li>anything else — Boot's own template (KafkaAvroSerializer): a record
     *   that decoded fine but whose processing failed is written to the DLT as
     *   the same Avro record, registered under {@code <topic>.DLT}'s own subject.</li>
     * </ul>
     * Both use the platform's shared {@code spring.kafka.producer.*} settings
     * ({@code acks=all}, idempotence, bounded timeouts). The recoverer sends
     * synchronously, so a parked record is acknowledged before its offset is
     * committed.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    @SuppressWarnings("unchecked")
    public CommonErrorHandler deadLetterErrorHandler(ObjectProvider<KafkaTemplate<?, ?>> kafkaTemplate,
                                                     ObjectProvider<MeterRegistry> meterRegistry,
                                                     KafkaErrorHandlingProperties properties) {
        KafkaTemplate<Object, Object> avroTemplate = (KafkaTemplate<Object, Object>) kafkaTemplate.getObject();
        KafkaTemplate<Object, Object> rawBytesTemplate = new KafkaTemplate<>(avroTemplate.getProducerFactory(),
                Map.<String, Object>of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawBytesTemplate);
        templates.put(Object.class, avroTemplate);
        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(templates,
                (record, exception) -> new TopicPartition(DeadLetterTopics.of(record.topic()), record.partition()));
        MeteredDeadLetterRecoverer recoverer = new MeteredDeadLetterRecoverer(deadLetterPublisher,
                meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        // Milestone 22 — deserialization failures are no longer fatal by type:
        // DeserializationFailureBackOff decides per failure (registry
        // unreachable: retry until it is back; anything else: DLT at once).
        errorHandler.removeClassification(DeserializationException.class);
        errorHandler.setBackOffFunction(new DeserializationFailureBackOff());
        return errorHandler;
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaAssignmentsEndpoint kafkaAssignmentsEndpoint(KafkaListenerEndpointRegistry registry) {
        return new KafkaAssignmentsEndpoint(registry);
    }
}
