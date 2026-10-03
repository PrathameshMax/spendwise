package com.spendwise.transactionservice.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Milestone 17 — {@code spendwise.outbox.publisher.*}. The poll interval
 * itself is read directly by {@code @Scheduled(fixedDelayString = ...)} on
 * {@link OutboxPublisher} (an annotation attribute must be a placeholder, not
 * a bean property), so it is documented in config-repo/transaction-service.yml
 * rather than bound here.
 *
 * @param batchSize          rows claimed per poll; bounds how long one poll
 *                           holds its row locks
 * @param topic              destination topic for every outbox row
 * @param partitions         partition count the publisher ensures the topic has
 * @param replicationFactor  replicas per partition (3 = one per broker)
 * @param minInSyncReplicas  with acks=all, the broker only acknowledges once this
 *                           many replicas hold the record — the definition of
 *                           "acknowledged" the publisher waits for
 * @param sendTimeout        upper bound on waiting for one acknowledgment before
 *                           the batch stops and the row is retried next poll
 */
@ConfigurationProperties("spendwise.outbox.publisher")
public record OutboxPublisherProperties(
        @DefaultValue("100") int batchSize,
        @DefaultValue("transaction-events") String topic,
        @DefaultValue("3") int partitions,
        @DefaultValue("3") short replicationFactor,
        @DefaultValue("2") int minInSyncReplicas,
        @DefaultValue("10s") Duration sendTimeout) {
}
