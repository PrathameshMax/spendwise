package com.spendwise.apigateway.config;

import com.spendwise.apigateway.filter.ProblemDetailRateLimitFilter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every route to a business service gets the same edge filters, in order:
 *
 * <ol>
 *   <li>Rate limit — Redis token bucket keyed by the authenticated user ({@code sub})
 *       or, for anonymous traffic, the client IP.</li>
 *   <li>Request size cap — oversized bodies are rejected with 413 before they reach
 *       a service.</li>
 *   <li>Token relay — for browser sessions, the access token held in the
 *       server-side session (refreshed with the refresh token when expired) is
 *       sent downstream as {@code Authorization: Bearer}; bearer-token API clients
 *       pass their own header through unchanged. If the refresh token has been
 *       revoked (logout, reuse detection), the call fails with 401 so the SPA
 *       starts a new login.</li>
 *   <li>{@code Cookie} removed — the BFF session cookie is a gateway credential and
 *       never travels to a microservice.</li>
 * </ol>
 *
 * Login, refresh and logout are no longer routes: they are Keycloak's job, driven
 * by the gateway's OAuth2 client ({@code /oauth2/authorization/keycloak},
 * {@code /logout}). The per-service {@code /v3/api-docs/<service>} routes exist
 * only when {@code spendwise.security.expose-api-docs} is on (dev).
 */
@Configuration
public class RouteConfig {

    private static final String API_DOCS_PATH = "/v3/api-docs";

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                     GatewayRouteProperties routeProperties,
                                     GatewaySecurityProperties securityProperties,
                                     ProblemDetailRateLimitFilter rateLimitFilter,
                                     TokenRelayGatewayFilterFactory tokenRelayFactory) {
        GatewayFilter tokenRelay = tokenRelayWithReauthentication(tokenRelayFactory);
        DataSize maxRequestSize = securityProperties.getMaxRequestSize();

        RouteLocatorBuilder.Builder routes = builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> edgeFilters(f, rateLimitFilter, tokenRelay, maxRequestSize))
                        .uri(routeProperties.uriFor("user-service")))
                .route("transaction-service", r -> r
                        .path("/api/v1/categories/**", "/api/v1/transactions/**")
                        .filters(f -> edgeFilters(f, rateLimitFilter, tokenRelay, maxRequestSize))
                        .uri(routeProperties.uriFor("transaction-service")))
                .route("budget-service", r -> r
                        .path("/api/v1/budgets/**")
                        .filters(f -> edgeFilters(f, rateLimitFilter, tokenRelay, maxRequestSize))
                        .uri(routeProperties.uriFor("budget-service")))
                .route("analytics-service", r -> r
                        .path("/api/v1/analytics/**")
                        .filters(f -> edgeFilters(f, rateLimitFilter, tokenRelay, maxRequestSize))
                        .uri(routeProperties.uriFor("analytics-service")));

        if (securityProperties.isExposeApiDocs()) {
            for (String serviceName : routeProperties.getRoutes().keySet()) {
                String docsPath = API_DOCS_PATH + "/" + serviceName;
                String targetUri = routeProperties.uriFor(serviceName);
                routes = routes.route(serviceName + "-docs", r -> r
                        .path(docsPath)
                        .filters(f -> f.filter(rateLimitFilter)
                                .removeRequestHeader(HttpHeaders.COOKIE)
                                .rewritePath(docsPath, API_DOCS_PATH))
                        .uri(targetUri));
            }
        }

        return routes.build();
    }

    private static GatewayFilterSpec edgeFilters(GatewayFilterSpec filterSpec,
                                                 ProblemDetailRateLimitFilter rateLimitFilter,
                                                 GatewayFilter tokenRelay,
                                                 DataSize maxRequestSize) {
        return filterSpec
                .filter(rateLimitFilter)
                .setRequestSize(maxRequestSize)
                .filter(tokenRelay)
                .removeRequestHeader(HttpHeaders.COOKIE);
    }

    private static GatewayFilter tokenRelayWithReauthentication(TokenRelayGatewayFilterFactory tokenRelayFactory) {
        GatewayFilter relay = tokenRelayFactory.apply();
        return (exchange, chain) -> relay.filter(exchange, chain)
                .onErrorMap(OAuth2AuthorizationException.class, error -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Session expired; log in again", error));
    }
}
