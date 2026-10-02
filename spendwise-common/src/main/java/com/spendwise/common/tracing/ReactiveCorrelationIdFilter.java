package com.spendwise.common.tracing;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Milestone 15 — the WebFlux-stack twin of {@link CorrelationIdFilter} (Milestone 3),
 * needed because {@link CorrelationIdFilter} extends {@code jakarta.servlet.http.HttpFilter}
 * — a type that does not exist on analytics-service's reactive Netty runtime at all, not
 * merely one it happens not to use.
 *
 * <p>Deliberately does NOT put the correlation id into the SLF4J MDC directly the way
 * {@link CorrelationIdFilter} does: MDC is backed by a plain {@link ThreadLocal}, and a
 * single WebFlux request is handed off across multiple different event-loop threads over
 * its own lifetime (a {@code flatMap}, a {@code Mono.create} callback, or any I/O
 * continuation can resume on a different thread than the one that started it) while those
 * same few event-loop threads concurrently interleave work for many OTHER requests at the
 * same time — an {@code MDC.put} here with no matching same-thread {@code remove()} would
 * leak this request's correlation id into some unrelated request's log lines the moment
 * this thread picks up different work, a hazard this platform's synchronous,
 * one-thread-per-request servlet services never have to consider. Reactor's {@link Context}
 * instead carries the correlation id as an immutable value attached to this request's own
 * reactive chain — not to any thread — explicitly written here via {@code contextWrite} and
 * read back out anywhere downstream, including past a thread-hop, via
 * {@code Mono.deferContextual(...)} (see {@link AbstractReactiveGlobalExceptionHandler}
 * sibling-package Javadoc) or, at the one place this milestone actually crosses a real
 * thread boundary mid-request, bridged into a narrowly-scoped {@code MDC.put}/{@code remove}
 * pair around a single synchronous callback
 * ({@code com.spendwise.analyticsservice.grpc.BudgetSummaryGrpcClient}).
 */
public class ReactiveCorrelationIdFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set(CorrelationIdConstants.HEADER_NAME, correlationId);

        String resolvedCorrelationId = correlationId;
        return chain.filter(exchange)
                .contextWrite(Context.of(CorrelationIdConstants.MDC_KEY, resolvedCorrelationId));
    }
}
