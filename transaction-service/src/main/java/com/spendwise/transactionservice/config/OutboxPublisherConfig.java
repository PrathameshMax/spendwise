package com.spendwise.transactionservice.config;

import com.spendwise.transactionservice.outbox.OutboxPublisherProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Milestone 17 — turns on Spring's scheduler for
 * {@link com.spendwise.transactionservice.outbox.OutboxPublisher}'s
 * {@code @Scheduled} poll and binds its properties. Boot's default
 * {@code ThreadPoolTaskScheduler} has a single thread, so polls on one
 * instance never overlap; cross-instance overlap is handled in SQL by
 * {@code FOR UPDATE SKIP LOCKED}, not here.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxPublisherProperties.class)
public class OutboxPublisherConfig {
}
