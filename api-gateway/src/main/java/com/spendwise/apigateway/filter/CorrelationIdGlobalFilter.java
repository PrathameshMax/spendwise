package com.spendwise.apigateway.filter;

import com.spendwise.common.tracing.CorrelationIdConstants;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
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
 *
 * <p>Milestone 13 fix (caught validating Milestone 12 — every response
 * carried the header twice): this filter used to add the header directly onto
 * {@code exchange.getResponse()} up front, before the request was even
 * routed. Every downstream business service ALSO echoes
 * {@value CorrelationIdConstants#HEADER_NAME} back on its own response (the
 * servlet-side {@code CorrelationIdFilter} this class's Javadoc already
 * points to), so by the time Spring Cloud Gateway's own internal routing
 * machinery copies the proxied response's headers onto the exchange, both
 * copies are present — and a {@code DedupeResponseHeader=X-Correlation-ID,
 * RETAIN_FIRST} default-filter (tried first) did not reliably win that race
 * against exactly when Gateway's own response-writing step performs that
 * header copy, as confirmed by live testing continuing to show the duplicate.
 * Decorating the response's {@code writeWith}/{@code setComplete} — the
 * actual commit points, called no matter how the response body is ultimately
 * produced — to {@code set()} (replace, not add) the header right there is
 * independent of Gateway's internal filter-ordering details: whatever else
 * has already been written into the header map by that point, this is always
 * the last word on it before a single byte reaches the client.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CorrelationIdConstants.HEADER_NAME, finalCorrelationId)
                .build();

        ServerHttpResponse decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                getHeaders().set(CorrelationIdConstants.HEADER_NAME, finalCorrelationId);
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                getHeaders().set(CorrelationIdConstants.HEADER_NAME, finalCorrelationId);
                return super.setComplete();
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .response(decoratedResponse)
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
