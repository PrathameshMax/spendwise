package com.spendwise.transactionservice.config;

import com.spendwise.common.messaging.JdbcProcessedEventGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Milestone 21 — opts this service into spendwise-common's
 * {@link JdbcProcessedEventGuard} over transaction_db's
 * {@code processed_events} table (V6 migration), now that
 * transaction-service consumes {@code budget-events}.
 */
@Configuration
public class IdempotencyConfig {

    @Bean
    public JdbcProcessedEventGuard processedEventGuard(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessedEventGuard(jdbcTemplate);
    }
}
