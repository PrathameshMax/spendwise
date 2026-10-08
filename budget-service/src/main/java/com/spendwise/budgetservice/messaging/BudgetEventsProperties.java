package com.spendwise.budgetservice.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Milestone 21 — {@code spendwise.budget-events.*}, the topic
 * {@link BudgetEventPublisher} owns, provisions and publishes to. Same
 * durability settings as {@code transaction-events} (Milestone 17): one
 * replica per broker, and acks=all acknowledged only once two replicas hold
 * the record.
 *
 * @param topic               destination topic; must match
 *                            {@code spendwise.kafka.topics.budget-events}, which
 *                            transaction-service's listener subscribes to
 * @param partitions          partition count; the record key is the transaction
 *                            id, so all decisions about one transaction stay on
 *                            one partition
 * @param replicationFactor   replicas per partition
 * @param minInSyncReplicas   replicas that must hold a record before acks=all
 *                            acknowledges it
 * @param sendTimeout         upper bound on waiting for that acknowledgment;
 *                            past it the decision rolls back and is retried
 * @param deadLetterRetention retention of {@code budget-events.DLT}, created
 *                            alongside the main topic for transaction-service's
 *                            dead-letter recoverer
 */
@ConfigurationProperties("spendwise.budget-events")
public record BudgetEventsProperties(
        @DefaultValue("budget-events") String topic,
        @DefaultValue("3") int partitions,
        @DefaultValue("3") short replicationFactor,
        @DefaultValue("2") int minInSyncReplicas,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("30d") Duration deadLetterRetention) {
}
