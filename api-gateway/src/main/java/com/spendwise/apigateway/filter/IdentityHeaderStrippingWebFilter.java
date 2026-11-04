package com.spendwise.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Hop ② — strips identity headers a client may try to inject (e.g.
 * {@code X-User-Id: <someone-else>}) before anything else runs, so no service
 * can ever be tricked into trusting a client-supplied identity. Identity
 * downstream comes only from the validated JWT. Runs as the very first WebFilter,
 * ahead of Spring Security.
 */
@Component
public class IdentityHeaderStrippingWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderStrippingWebFilter.class);

    static final List<String> SPOOFABLE_HEADERS = List.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Roles",
            "X-User-Context",
            "X-Authenticated-User",
            "X-Forwarded-User",
            "X-Forwarded-Email",
            "X-Auth-Request-User",
            "X-Auth-Request-Email",
            "X-Remote-User");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        List<String> present = SPOOFABLE_HEADERS.stream().filter(headers::containsKey).toList();
        if (present.isEmpty()) {
            return chain.filter(exchange);
        }
        log.warn("security_event=SPOOFED_IDENTITY_HEADER_STRIPPED headers={} path={}",
                present.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList(),
                exchange.getRequest().getPath().value());
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(mutable -> present.forEach(mutable::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
