package com.spendwise.notificationservice.config;

import com.spendwise.common.messaging.JdbcProcessedEventGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Milestone 21 — opts this service into spendwise-common's
 * {@link JdbcProcessedEventGuard} (moved there from this service's own
 * {@code messaging} package), over notification_db's
 * {@code processed_events} table (V1 migration).
 */
@Configuration
public class IdempotencyConfig {

    @Bean
    public JdbcProcessedEventGuard processedEventGuard(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessedEventGuard(jdbcTemplate);
    }
}
