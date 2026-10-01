package com.spendwise.apigateway.config;

import com.spendwise.apigateway.security.RefreshCookieSupport;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares every route the Gateway proxies to a business service (Milestone 6).
 * Auth routes are split into three so the cookie-rotation filters
 * ({@link RefreshCookieSupport}) apply only where they are meaningful:
 * register/login mint a fresh pair (response-side cookie rotation only),
 * refresh exchanges a token supplied purely via cookie (both request- and
 * response-side rewriting), and the catch-all covers the read-only
 * {@code GET /api/v1/auth/{id}} endpoint, which needs neither. Order matters:
 * Spring Cloud Gateway matches routes in declaration order, so the specific
 * auth paths must be declared before the {@code /api/v1/auth/**} catch-all.
 *
 * user-service, transaction-service and budget-service are plain
 * JWT-protected passthroughs — {@link SecurityConfig} and
 * {@code UserContextPropagationGlobalFilter} handle authentication and
 * identity forwarding for every route uniformly, so no route-specific filter
 * is needed there beyond the rate limiter every route below gets.
 * notification-service and analytics-service have no REST surface yet
 * (Milestones 19 and 15 respectively), so they are not routed here for
 * business traffic until those milestones give them one.
 *
 * Milestone 7 adds one route per service purely to proxy its OpenAPI
 * document ({@code /v3/api-docs/<service-name>} -> that service's own
 * {@code /v3/api-docs}) onto the Gateway's own origin. This exists because
 * Swagger UI fetches each spec via browser JS: fetching directly from
 * auth-service's port (8081) while the aggregator page is served from the
 * Gateway's port (8080) is a cross-origin request, and proxying through the
 * same origin avoids needing to configure CORS on every business service
 * just to let their docs render. This loop covers every entry in
 * {@code spendwise.gateway.routes} — including notification-service and
 * analytics-service, which are absent from the business routes above but
 * still get their (currently near-empty) OpenAPI document proxied, matching
 * the "wire springdoc into every service" scope of this milestone.
 *
 * Milestone 14 applies {@code requestRateLimiter} (backed by
 * {@link RateLimitConfig}'s {@link RedisRateLimiter}/{@link KeyResolver}
 * beans) to every route declared here, including the docs-proxy loop — in
 * code, not via {@code spring.cloud.gateway.default-filters}, because that
 * YAML mechanism only attaches to routes sourced from a
 * {@code RouteDefinitionLocator} (YAML/properties-defined routes) and
 * silently does nothing for routes built through this class's
 * {@code RouteLocatorBuilder} fluent calls — confirmed the hard way, by a
 * Chaos Lab run that sent 25 requests past the configured burst capacity and
 * got zero {@code 429}s and zero Redis keys back (see
 * {@link RateLimitConfig}'s Javadoc for the full root-cause trail,
 * including the upstream issue that documents the exact same mismatch).
 */
@Configuration
public class RouteConfig {

    private static final String API_DOCS_PATH = "/v3/api-docs";

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                      GatewayRouteProperties routeProperties,
                                      GatewaySecurityProperties securityProperties,
                                      RedisRateLimiter redisRateLimiter,
                                      KeyResolver remoteAddressKeyResolver) {
        String authServiceUri = routeProperties.uriFor("auth-service");

        RouteLocatorBuilder.Builder routes = builder.routes()
                .route("auth-service-issuance", r -> r
                        .path("/api/v1/auth/register", "/api/v1/auth/login")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver)
                                .modifyResponseBody(String.class, String.class,
                                        (exchange, body) -> RefreshCookieSupport.rotateRefreshCookie(exchange, securityProperties, body)))
                        .uri(authServiceUri))
                .route("auth-service-refresh", r -> r
                        .path("/api/v1/auth/refresh")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver)
                                .modifyRequestBody(String.class, String.class,
                                        (exchange, body) -> RefreshCookieSupport.injectRefreshTokenFromCookie(exchange, securityProperties, body))
                                .modifyResponseBody(String.class, String.class,
                                        (exchange, body) -> RefreshCookieSupport.rotateRefreshCookie(exchange, securityProperties, body)))
                        .uri(authServiceUri))
                .route("auth-service-passthrough", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver))
                        .uri(authServiceUri))
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver))
                        .uri(routeProperties.uriFor("user-service")))
                .route("transaction-service", r -> r
                        .path("/api/v1/categories/**", "/api/v1/transactions/**")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver))
                        .uri(routeProperties.uriFor("transaction-service")))
                .route("budget-service", r -> r
                        .path("/api/v1/budgets/**")
                        .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver))
                        .uri(routeProperties.uriFor("budget-service")));

        for (String serviceName : routeProperties.getRoutes().keySet()) {
            String docsPath = API_DOCS_PATH + "/" + serviceName;
            String targetUri = routeProperties.uriFor(serviceName);
            routes = routes.route(serviceName + "-docs", r -> r
                    .path(docsPath)
                    .filters(f -> rateLimited(f, redisRateLimiter, remoteAddressKeyResolver)
                            .rewritePath(docsPath, API_DOCS_PATH))
                    .uri(targetUri));
        }

        return routes.build();
    }

    /**
     * Shared by every route above rather than duplicating the
     * {@code c.setRateLimiter(...).setKeyResolver(...)} lambda at each call
     * site — {@link GatewayFilterSpec#requestRateLimiter} returns the same
     * {@link GatewayFilterSpec}, so callers keep chaining their own
     * route-specific filters straight off this method's return value exactly
     * as they would off {@code f} itself.
     */
    private static GatewayFilterSpec rateLimited(GatewayFilterSpec filterSpec,
                                                  RedisRateLimiter redisRateLimiter,
                                                  KeyResolver keyResolver) {
        return filterSpec.requestRateLimiter(c -> c.setRateLimiter(redisRateLimiter).setKeyResolver(keyResolver));
    }
}
