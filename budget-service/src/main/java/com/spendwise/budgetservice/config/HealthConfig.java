package com.spendwise.budgetservice.config;

import com.spendwise.common.health.DeadlockHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers spendwise-common's {@link DeadlockHealthIndicator} under the bean
 * name "deadlockHealthIndicator" — Spring Boot Actuator strips the "HealthIndicator"
 * suffix to derive the component id "deadlock", which config-repo/application.yml
 * (Milestone 5) adds to the liveness health group.
 */
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator deadlockHealthIndicator() {
        return new DeadlockHealthIndicator();
    }
}
