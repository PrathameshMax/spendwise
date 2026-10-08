package com.spendwise.budgetservice.config;

import com.spendwise.budgetservice.messaging.BudgetEventsProperties;
import com.spendwise.common.messaging.JdbcProcessedEventGuard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Milestone 21 — budget-service's messaging wiring: opts into
 * spendwise-common's {@link JdbcProcessedEventGuard} over budget_db's
 * {@code processed_events} table (V2 migration), and binds
 * {@code spendwise.budget-events.*} for the publisher.
 */
@Configuration
@EnableConfigurationProperties(BudgetEventsProperties.class)
public class MessagingConfig {

    @Bean
    public JdbcProcessedEventGuard processedEventGuard(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessedEventGuard(jdbcTemplate);
    }
}
