package com.spendwise.apigateway.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The BFF authenticates the browser with a cookie, so state-changing requests
 * need CSRF protection. {@code CookieServerCsrfTokenRepository} creates the
 * {@code XSRF-TOKEN} cookie lazily — only when the token is subscribed to. This
 * filter subscribes on every request so the SPA always has the cookie to echo
 * back in the {@code X-XSRF-TOKEN} header. Bearer-token API clients are exempt
 * (the resource-server configuration excludes them from CSRF checks).
 */
public class CsrfCookieWebFilter implements WebFilter {

    private static final String CSRF_TOKEN_ATTRIBUTE = CsrfToken.class.getName();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CSRF_TOKEN_ATTRIBUTE);
        if (csrfToken == null) {
            return chain.filter(exchange);
        }
        return csrfToken.then(chain.filter(exchange));
    }
}
