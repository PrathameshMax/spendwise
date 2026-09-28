package com.spendwise.notificationservice.config;

import com.spendwise.common.health.DeadlockHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers spendwise-common's {@link DeadlockHealthIndicator} under the bean
 * name "deadlockHealthIndicator" — Spring Boot Actuator strips the "HealthIndicator"
 * suffix to derive the component id "deadlock", which config-repo/application.yml
 * (Milestone 5) adds to the liveness health group.
 *
 * notification-service has no web-facing endpoints yet, but liveness/deadlock
 * detection is JVM-level, not HTTP-level, and the "liveness" health group is
 * defined globally in config-repo/application.yml and applies to every client
 * of the Config Server. Spring Boot's default
 * management.endpoint.health.validate-group-membership=true fails application
 * startup for any service whose registered health contributors do not satisfy
 * every group member, so this bean must exist here exactly as it does in
 * auth-service, user-service, transaction-service, budget-service and
 * analytics-service.
 */
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator deadlockHealthIndicator() {
        return new DeadlockHealthIndicator();
    }
}
