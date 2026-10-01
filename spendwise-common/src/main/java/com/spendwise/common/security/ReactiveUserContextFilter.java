package com.spendwise.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.security.GeneralSecurityException;

/**
 * Milestone 15 — the WebFlux-stack twin of {@link UserContextFilter} (Milestone 6),
 * needed for the identical reason as
 * {@link com.spendwise.common.tracing.ReactiveCorrelationIdFilter}: {@link UserContextFilter}
 * extends the servlet-only {@code jakarta.servlet.http.HttpFilter}.
 *
 * <p>Publishes the verified {@link UserContext} into Reactor {@link Context} under
 * {@link UserContextConstants#REACTOR_CONTEXT_KEY} rather than the {@link UserContextHolder}
 * {@link ThreadLocal} every servlet-based service uses, for the identical thread-safety
 * reason {@code ReactiveCorrelationIdFilter} does not use the SLF4J MDC directly — a value
 * set on one event-loop thread is not reliably visible, or safe to read, on whichever thread
 * later continues this same request's reactive chain.
 *
 * <p>No code anywhere in this platform reads caller identity yet (this milestone's own
 * platform-wide search found zero {@code UserContextHolder.get()} call sites outside its
 * own filter/holder pair), so this filter exists purely to keep analytics-service at parity
 * with every other service's cross-cutting filter chain, ready the moment a reactive
 * consumer needs it. Any future one must read it via
 * {@code Mono.deferContextual(ctx -> ctx.getOrDefault(UserContextConstants.REACTOR_CONTEXT_KEY, null))},
 * never {@code UserContextHolder.get()}, which would silently return null — or, worse, a
 * different request's value — under WebFlux's multiplexed-thread execution model.
 */
public class ReactiveUserContextFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReactiveUserContextFilter.class);

    private final String sharedSecret;

    public ReactiveUserContextFilter(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(UserContextConstants.HEADER_NAME);
        if (header == null || header.isBlank()) {
            return chain.filter(exchange);
        }

        try {
            UserContext userContext = UserContextVerifier.verifyAndParse(header, sharedSecret);
            return chain.filter(exchange)
                    .contextWrite(Context.of(UserContextConstants.REACTOR_CONTEXT_KEY, userContext));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.debug("Discarding invalid {} header: {}", UserContextConstants.HEADER_NAME, e.getMessage());
            return chain.filter(exchange);
        }
    }
}
