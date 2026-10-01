package com.spendwise.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Milestone 14 — the {@link KeyResolver} bean that
 * {@code spring.cloud.gateway.default-filters}' {@code RequestRateLimiter}
 * entry (config-repo/api-gateway.yml) references via
 * {@code #{@remoteAddressKeyResolver}}.
 *
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
 * This bean is wired through a {@code default-filter}, not a filter scoped
 * to the auth routes alone, so every route — not only auth-service's — gets
 * the same IP-bucketed protection, matching the roadmap's "before they reach
 * any core service" wording as defense in depth, even though login/register
 * remain the routes most exposed to this threat (every other route sits
 * behind {@link SecurityConfig}'s JWT check, which rejects a forged or
 * missing token before the request ever reaches this rate limiter).
 */
@Configuration
public class RateLimitConfig {

    private static final String UNRESOLVED_ADDRESS_KEY = "unresolved-remote-address";

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
