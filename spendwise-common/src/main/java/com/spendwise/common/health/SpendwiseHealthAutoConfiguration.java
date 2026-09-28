package com.spendwise.common.health;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
public class SpendwiseHealthAutoConfiguration {

    @Bean(name = "deadlockHealthIndicator")
    @ConditionalOnMissingBean(DeadlockHealthIndicator.class)
    public DeadlockHealthIndicator deadlockHealthIndicator() {
        return new DeadlockHealthIndicator();
    }
}
