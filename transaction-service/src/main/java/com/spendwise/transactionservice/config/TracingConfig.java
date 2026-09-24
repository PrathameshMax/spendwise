package com.spendwise.transactionservice.config;

import com.spendwise.common.tracing.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link CorrelationIdFilter} from spendwise-common so every
 * request handled by this service seeds/propagates X-Correlation-ID into the MDC,
 * regardless of whether it arrived from the (not-yet-built) API Gateway or directly.
 */
@Configuration
public class TracingConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("correlationIdFilter");
        return registration;
    }
}
