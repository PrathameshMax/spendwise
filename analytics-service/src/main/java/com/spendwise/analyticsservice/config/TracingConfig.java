package com.spendwise.analyticsservice.config;

import com.spendwise.common.tracing.ReactiveCorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link ReactiveCorrelationIdFilter} from spendwise-common
 * so every request handled by this service propagates a correlation id into the
 * Reactor {@code Context}, regardless of whether it arrived from API Gateway or
 * directly. Milestone 15 rebuild of this class: a WebFlux {@code WebFilter} bean
 * is auto-detected and ordered by Spring directly (via {@link org.springframework.core.Ordered},
 * which {@link ReactiveCorrelationIdFilter} implements) — unlike the servlet
 * stack's {@code FilterRegistrationBean}, which this class previously returned,
 * there is no url-pattern registration step, since every WebFlux {@code WebFilter}
 * bean already applies to every request by construction.
 */
@Configuration
public class TracingConfig {

    @Bean
    public ReactiveCorrelationIdFilter correlationIdFilter() {
        return new ReactiveCorrelationIdFilter();
    }
}
