package com.spendwise.apigateway.config;

import com.spendwise.apigateway.security.RefreshCookieSupport;
import org.springframework.cloud.gateway.route.RouteLocator;
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
 * is needed there. notification-service and analytics-service have no REST
 * surface yet (Milestones 19 and 15 respectively), so they are not routed
 * here until those milestones give them one.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                      GatewayRouteProperties routeProperties,
                                      GatewaySecurityProperties securityProperties) {
        String authServiceUri = routeProperties.uriFor("auth-service");

        return builder.routes()
                .route("auth-service-issuance", r -> r
                        .path("/api/v1/auth/register", "/api/v1/auth/login")
                        .filters(f -> f.modifyResponseBody(String.class, String.class,
                                (exchange, body) -> RefreshCookieSupport.rotateRefreshCookie(exchange, securityProperties, body)))
                        .uri(authServiceUri))
                .route("auth-service-refresh", r -> r
                        .path("/api/v1/auth/refresh")
                        .filters(f -> f
                                .modifyRequestBody(String.class, String.class,
                                        (exchange, body) -> RefreshCookieSupport.injectRefreshTokenFromCookie(exchange, securityProperties, body))
                                .modifyResponseBody(String.class, String.class,
                                        (exchange, body) -> RefreshCookieSupport.rotateRefreshCookie(exchange, securityProperties, body)))
                        .uri(authServiceUri))
                .route("auth-service-passthrough", r -> r
                        .path("/api/v1/auth/**")
                        .uri(authServiceUri))
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .uri(routeProperties.uriFor("user-service")))
                .route("transaction-service", r -> r
                        .path("/api/v1/categories/**", "/api/v1/transactions/**")
                        .uri(routeProperties.uriFor("transaction-service")))
                .route("budget-service", r -> r
                        .path("/api/v1/budgets/**")
                        .uri(routeProperties.uriFor("budget-service")))
                .build();
    }
}
