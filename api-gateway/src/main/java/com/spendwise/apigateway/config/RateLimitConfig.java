package com.spendwise.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.apigateway.filter.ProblemDetailRateLimitFilter;
import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final String UNRESOLVED_ADDRESS_KEY = "unresolved";
    private static final String USER_KEY_PREFIX = "user:";
    private static final String IP_KEY_PREFIX = "ip:";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // Shape checks only — enough to reject hostnames and junk without any
    // resolution. Octet ranges are not validated: a malformed-but-numeric
    // value simply becomes its own bucket key, which is harmless.
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6_LITERAL = Pattern.compile("^(?=.*:)[0-9A-Fa-f:.]{2,45}$");

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
     * Milestone 17 fix — key resolution order: authenticated principal, then
     * client IP (X-Forwarded-For, then the socket address), never empty.
     *
     * <p><b>Principal first.</b> Milestone 14 keyed every request by IP alone,
     * which made every user behind one NAT, corporate proxy or mobile carrier
     * gateway share one 20-token bucket — one heavy user throttled all of
     * them. An authenticated request is now bucketed by its JWT subject
     * ({@code user:<sub>}), so each user gets their own limit regardless of
     * network location. {@code exchange.getPrincipal()} is populated by Spring
     * Security's WebFilter chain, which runs before any Gateway filter.
     *
     * <p><b>IP for everything without a principal.</b> {@code POST
     * /api/v1/auth/login} and {@code /register} are {@code permitAll()} — the
     * brute-force targets Milestone 14 exists to protect — and carry no
     * principal, so they fall through to {@code ip:<address>}. The {@code user:}
     * / {@code ip:} prefixes keep the two key spaces from ever colliding.
     *
     * <p><b>X-Forwarded-For.</b> Behind a load balancer, the socket address is
     * the balancer's, not the client's, so every client would share one bucket.
     * The client IP is taken from X-Forwarded-For at position
     * {@code size - trustedProxyHops} (counted from the right): each trusted
     * proxy appends the address it received the connection from, so the entry
     * the outermost trusted proxy appended is the first one a client cannot
     * forge. Entries further left are client-supplied. If the gateway is
     * exposed directly (no proxy in front), every X-Forwarded-For value is
     * client-supplied and a client can rotate it to get a fresh bucket per
     * request — set {@code spendwise.rate-limit.trusted-proxy-hops: 0}
     * in that deployment to ignore the header entirely. The selected entry is
     * accepted only if it is an IPv4/IPv6 literal; it is never passed to
     * {@code InetAddress}/{@code InetSocketAddress}, which would trigger a
     * blocking DNS lookup on a Netty event-loop thread for a hostname-shaped
     * value. Anything unusable falls back to the socket address.
     *
     * <p><b>Never empty.</b> {@code Mono.empty()} from a KeyResolver makes the
     * rate limiter deny the request outright; the final fallback is a shared
     * {@code ip:unresolved} bucket instead.
     */
    @Bean
    public KeyResolver clientKeyResolver(
            @Value("${spendwise.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        return exchange -> exchange.getPrincipal()
                .filter(RateLimitConfig::isAuthenticatedPrincipal)
                .map(Principal::getName)
                .filter(StringUtils::hasText)
                .map(name -> USER_KEY_PREFIX + name)
                .switchIfEmpty(Mono.fromSupplier(() -> IP_KEY_PREFIX + resolveClientIp(exchange, trustedProxyHops)));
    }

    /**
     * Milestone 17 fix — the filter {@link RouteConfig} applies to every route
     * in place of the built-in {@code requestRateLimiter}, so a rejected
     * request gets an RFC 7807 body and a {@code Retry-After} header instead
     * of an empty 429.
     *
     * <p>{@code Retry-After} is the time until one more request's worth of
     * tokens has been replenished: {@code ceil(requestedTokens / replenishRate)}
     * seconds, never less than 1 (the header's integer-seconds granularity).
     * With 10 tokens/s and 1 token per request, that is 1 second.
     */
    @Bean
    public ProblemDetailRateLimitFilter problemDetailRateLimitFilter(RedisRateLimiter redisRateLimiter,
                                                                     KeyResolver clientKeyResolver,
                                                                     ObjectMapper objectMapper) {
        long retryAfterSeconds = Math.max(1L, (REQUESTED_TOKENS + REPLENISH_RATE - 1L) / REPLENISH_RATE);
        return new ProblemDetailRateLimitFilter(redisRateLimiter, clientKeyResolver, objectMapper, retryAfterSeconds);
    }

    static String resolveClientIp(ServerWebExchange exchange, int trustedProxyHops) {
        if (trustedProxyHops > 0) {
            List<String> forwardedFor = exchange.getRequest().getHeaders().get(X_FORWARDED_FOR);
            // More than one X-Forwarded-For header line is ambiguous (which one
            // did the trusted proxy write?) — ignored rather than guessed at.
            if (forwardedFor != null && forwardedFor.size() == 1) {
                String[] hops = StringUtils.tokenizeToStringArray(forwardedFor.get(0), ",");
                if (hops.length > 0) {
                    String candidate = hops[Math.max(0, hops.length - trustedProxyHops)].trim();
                    if (isIpLiteral(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNRESOLVED_ADDRESS_KEY;
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * Excludes an anonymous token: if anonymous authentication were ever
     * enabled, every unauthenticated caller would otherwise share the single
     * bucket {@code user:anonymousUser}.
     */
    private static boolean isAuthenticatedPrincipal(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * Milestone 17 fix — fail fast when Redis is down. Lettuce's default
     * {@code DisconnectedBehavior} is to queue commands issued while the
     * connection is down and replay them on reconnect, so with Redis stopped
     * every rate-limit check sat in that queue until {@code spring.data.redis.timeout}
     * expired — measured at ~2s added to every request. {@code REJECT_COMMANDS}
     * fails the command immediately while disconnected; RedisRateLimiter
     * catches that error and fails open (allows the request), so a Redis
     * outage now costs near-zero latency instead of the full timeout.
     * Auto-reconnect stays on, so limiting resumes as soon as Redis returns.
     * Applied through Boot's {@link LettuceClientOptionsBuilderCustomizer},
     * which runs after Boot has set the connect timeout and timeout options
     * from {@code spring.data.redis.*}, so those are kept, not replaced.
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer rejectCommandsWhileDisconnected() {
        return builder -> builder
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true);
    }

    static boolean isIpLiteral(String value) {
        return IPV4_LITERAL.matcher(value).matches() || IPV6_LITERAL.matcher(value).matches();
    }
}
