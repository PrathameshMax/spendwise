package com.spendwise.common.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.RecordInterceptor;

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
 * transaction-service also has spring-kafka (it produces) but no listener;
 * the beans there are inert, and the endpoint is not exposed there.
 *
 * <p>The meter registry is resolved through {@link ObjectProvider} rather
 * than guarded with {@code @ConditionalOnBean}: a bean condition in an
 * auto-configuration depends on the order other auto-configurations were
 * processed in, and could silently drop the interceptor. Every service with a
 * listener has Actuator, so the application's registry is always there; the
 * global registry is only a defensive fallback.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass(RecordInterceptor.class)
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

    @Bean
    @ConditionalOnMissingBean
    public KafkaAssignmentsEndpoint kafkaAssignmentsEndpoint(KafkaListenerEndpointRegistry registry) {
        return new KafkaAssignmentsEndpoint(registry);
    }
}
