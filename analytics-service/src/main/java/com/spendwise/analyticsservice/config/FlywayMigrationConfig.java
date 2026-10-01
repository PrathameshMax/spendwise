package com.spendwise.analyticsservice.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milestone 15 — analytics-service's first real schema migration
 * (dashboard_query_log), and this platform's first service on the reactive
 * stack. Spring Boot's own auto-configured Flyway bean (excluded on
 * {@link com.spendwise.analyticsservice.AnalyticsServiceApplication}) resolves
 * its JDBC connection from a {@code DataSource}/{@code DataSourceProperties}
 * bean — exactly what every JPA-based service on this platform already gets
 * for free from {@code spring-boot-starter-data-jpa}'s auto-configured
 * {@code DataSource}. This service has no such bean: Spring Data R2DBC
 * configures a reactive {@code ConnectionFactory} instead
 * ({@code spring.r2dbc.url}), and Flyway itself has no R2DBC support at all —
 * it only ever speaks JDBC. This bean therefore builds its own short-lived
 * JDBC connection directly from {@code spring.flyway.*} properties via
 * Flyway's own fluent configuration API, entirely independent of the
 * {@code ConnectionFactory} the rest of this service uses to actually serve
 * requests — the two never share a connection pool, driver, or lifecycle.
 * {@code initMethod = "migrate"} runs the migration once, at application
 * startup, before the reactive web server starts accepting traffic, the same
 * "migrate before serving" guarantee every JPA-based service's auto-configured
 * Flyway bean already provides.
 */
@Configuration
public class FlywayMigrationConfig {

    @Value("${spring.flyway.url}")
    private String flywayUrl;

    @Value("${spring.flyway.user}")
    private String flywayUser;

    @Value("${spring.flyway.password}")
    private String flywayPassword;

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(flywayUrl, flywayUser, flywayPassword)
                .load();
    }
}
