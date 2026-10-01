package com.spendwise.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Milestone 14 — the {@link KeyResolver} and {@link RedisRateLimiter} beans
 * {@link RouteConfig} wires into every route's {@code requestRateLimiter}
 * filter.
 *
 * Originally this platform's token-bucket settings
 * (replenishRate/burstCapacity/requestedTokens) lived in
 * {@code spring.cloud.gateway.default-filters} (config-repo/api-gateway.yml)
 * instead of here, on the assumption that a {@code default-filter} applies
 * platform-wide regardless of how a route is declared. Live validation
 * disproved that: every route in this platform is built via
 * {@code RouteLocatorBuilder}'s Java fluent DSL ({@link RouteConfig}), and
 * {@code default-filters} only attaches to routes sourced from a
 * {@code RouteDefinitionLocator} (YAML/properties-defined routes) — confirmed
 * against a maintainer-acknowledged report of the exact same mismatch,
 * <a href="https://github.com/spring-cloud/spring-cloud-gateway/issues/3121">spring-cloud-gateway#3121</a>.
 * With zero YAML-defined routes in this codebase, the default-filter was
 * configured but never attached to anything — Redis itself connected fine
 * (Spring Boot's Redis health indicator reported {@code UP}), but 25
 * consecutive requests past the configured burst capacity all still reached
 * auth-service, and {@code KEYS 'request_rate_limiter.*'} against Redis came
 * back empty, proving the filter was never actually invoked. The fix is the
 * one a working reference example confirms
 * (<a href="https://www.baeldung.com/spring-cloud-gateway-rate-limit-by-client-ip">Baeldung,
 * Rate Limiting With Client IP in Spring Cloud Gateway</a>; see also
 * <a href="https://github.com/spring-cloud/spring-cloud-gateway/issues/2246">spring-cloud-gateway#2246</a>,
 * which is why the rate values are set on {@code RedisRateLimiter}'s own
 * constructor here rather than on {@code RequestRateLimiterGatewayFilterFactory.Config}
 * — that Config object exposes only {@code setRateLimiter}/{@code setKeyResolver},
 * not the token-bucket parameters themselves): declare the
 * {@code RedisRateLimiter} as an explicit bean (its constructor's three
 * {@code int} arguments are replenishRate, burstCapacity, requestedTokens —
 * unchanged values, 10/20/1, from the inert YAML attempt) and apply it to
 * every route directly, in code, in {@link RouteConfig}.
 */
@Configuration
public class RateLimitConfig {

    private static final String UNRESOLVED_ADDRESS_KEY = "unresolved-remote-address";

    private static final int REPLENISH_RATE = 10;
    private static final int BURST_CAPACITY = 20;
    private static final int REQUESTED_TOKENS = 1;

    /**
     * Declaring this bean explicitly (rather than relying on
     * {@code GatewayRedisAutoConfiguration}'s own default-config
     * {@code RedisRateLimiter}, which is only reachable via YAML/properties
     * configuration of its own defaults) is what lets
     * replenishRate/burstCapacity/requestedTokens be set at all when every
     * route is wired up in Java, per {@link RouteConfig}'s Javadoc above.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(REPLENISH_RATE, BURST_CAPACITY, REQUESTED_TOKENS);
    }

    /**
     * Deliberately keyed by client IP rather than Spring Cloud Gateway's default
     * {@code PrincipalNameKeyResolver} (the authenticated principal's name): the
     * roadmap's own stated target is "brute-force or malicious bursts," and the
     * textbook example of that threat is exactly {@code POST
     * /api/v1/auth/login} and {@code /register} — both {@code permitAll()} in
     * {@link SecurityConfig} and therefore carrying no authenticated principal
     * at all. A principal-keyed resolver would have nothing to key on for
     * precisely the two endpoints this milestone exists to protect, and since
     * Spring Cloud Gateway denies a request outright whenever its KeyResolver
     * produces no key, that would mean blocking all unauthenticated traffic
     * rather than rate-limiting it. A client's IP address is the one identity
     * every inbound request has, authenticated or not, which is also why it is
     * the standard choice for gateway-level anti-brute-force throttling (Q51).
     *
     * {@link RouteConfig} applies this filter to every declared route, not
     * just the auth routes, so every route gets the same IP-bucketed
     * protection, matching the roadmap's "before they reach any core service"
     * wording as defense in depth — even though login/register remain the
     * routes most exposed to this threat (every other route sits behind
     * {@link SecurityConfig}'s JWT check, which rejects a forged or missing
     * token before the request ever reaches this rate limiter).
     */
    @Bean
    public KeyResolver remoteAddressKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                // Always emits a key rather than Mono.empty(): Spring Cloud Gateway's
                // default deny-empty-key behavior would otherwise reject every request
                // whose remote address genuinely cannot be resolved (certain test
                // harnesses, non-socket transports) outright — bucketing those together
                // under one shared key is a safer failure mode than denying them all.
                return Mono.just(UNRESOLVED_ADDRESS_KEY);
            }
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}
