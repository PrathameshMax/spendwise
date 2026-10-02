package com.spendwise.analyticsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

/**
 * {@code FlywayAutoConfiguration} is excluded (Milestone 15): Boot's own
 * auto-configured Flyway bean resolves its JDBC connection from a
 * {@code DataSource} bean, which this reactive, R2DBC-only service never
 * creates (no JDBC starter is on its classpath, only the R2DBC driver and a
 * bare JDBC driver jar for Flyway's own direct use below) — leaving that
 * auto-configuration enabled would have it silently never activate (no
 * migration ever runs) rather than fail loudly, the worse of the two
 * outcomes. {@link com.spendwise.analyticsservice.config.FlywayMigrationConfig}
 * replaces it with an explicit bean that builds its own short-lived JDBC
 * connection directly from {@code spring.flyway.*} properties instead.
 */
@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
