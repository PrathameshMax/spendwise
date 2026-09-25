package com.spendwise.configserver.config;

import com.spendwise.common.health.DeadlockHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator deadlockHealthIndicator() {
        return new DeadlockHealthIndicator();
    }
}
