package com.spendwise.apigateway.filter;

import com.spendwise.common.tracing.CorrelationIdConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebFlux/reactive equivalent of spendwise-common's servlet-based
 * {@link com.spendwise.common.tracing.CorrelationIdFilter}: Spring Cloud
 * Gateway runs on Netty, not a servlet container, so the shared filter (which
 * extends {@code jakarta.servlet.http.HttpFilter}) cannot be reused here
 * directly. This filter seeds/propagates the same
 * {@value CorrelationIdConstants#HEADER_NAME} header so every downstream
 * service — servlet-based or not — sees one consistent correlation ID for the
 * whole request, whether it originates at the Gateway or arrives already set.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CorrelationIdConstants.HEADER_NAME, correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().add(CorrelationIdConstants.HEADER_NAME, correlationId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
