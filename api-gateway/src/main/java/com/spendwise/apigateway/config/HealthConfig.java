package com.spendwise.apigateway.config;

import com.spendwise.common.health.DeadlockHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers spendwise-common's {@link DeadlockHealthIndicator} under the bean
 * name "deadlockHealthIndicator" — Spring Boot Actuator strips the
 * "HealthIndicator" suffix to derive the component id "deadlock", which
 * config-repo/application.yml (Milestone 5) adds to the liveness health group
 * for every client of the Config Server, api-gateway included. A plain
 * {@link HealthIndicator} bean is auto-adapted into a reactive health
 * contributor by Spring Boot Actuator, so the exact same indicator class used
 * by the servlet-based services works here on WebFlux without modification.
 * Omitting this (as notification-service did before its Milestone 5 hotfix)
 * fails startup with "Included health contributor 'deadlock' in group
 * 'liveness' does not exist".
 */
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator deadlockHealthIndicator() {
        return new DeadlockHealthIndicator();
    }
}
