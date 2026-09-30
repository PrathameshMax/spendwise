package com.spendwise.common.tracing;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Propagates {@link CorrelationIdConstants#HEADER_NAME} onto every outgoing
 * Feign call (Milestone 10) the same way {@link CorrelationIdFilter} does for
 * inbound servlet requests and {@code CorrelationIdGlobalFilter} does for the
 * reactive API Gateway: a synchronous inter-service call (Transaction/Budget
 * Service &rarr; User Service) must carry the caller's correlation ID forward,
 * or the two services' log lines for the same logical request become
 * uncorrelated the moment the call leaves the calling JVM.
 *
 * <p>Only activates when Feign is actually on the classpath
 * ({@code spring-cloud-starter-openfeign}, declared by transaction-service
 * and budget-service, not by every service) — this is why the dependency this
 * class compiles against is marked {@code optional} in this module's POM
 * rather than pulled in transitively everywhere.
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class SpendwiseFeignTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFeignRequestInterceptor")
    public RequestInterceptor correlationIdFeignRequestInterceptor() {
        return requestTemplate -> {
            String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                requestTemplate.header(CorrelationIdConstants.HEADER_NAME, correlationId);
            }
        };
    }
}
