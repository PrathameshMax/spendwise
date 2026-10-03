package com.spendwise.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.tracing.CorrelationIdConstants;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milestone 17 fix — token-bucket rate limiting whose rejection is an RFC 7807
 * problem document with a {@code Retry-After} header, replacing the built-in
 * {@code RequestRateLimiterGatewayFilterFactory} filter on every route.
 *
 * <p>The built-in filter rejects with {@code exchange.getResponse().setComplete()}:
 * status 429 and an empty body, which a client cannot distinguish from any
 * other empty 429 and which carries nothing telling it when to retry. Its
 * rejection branch has no extension point, so this filter performs the same
 * three steps itself — resolve the key, ask {@link RedisRateLimiter}, copy the
 * {@code X-RateLimit-*} headers onto the response — and differs only in what
 * it writes when the answer is "no". The limiter itself is unchanged: the same
 * bean, the same Redis Lua script, and the same fail-open behaviour when Redis
 * is unreachable ({@code RedisRateLimiter} answers "allowed" on any Redis
 * error).
 *
 * <p>The body has the same shape the business services' global exception
 * handlers return (RFC 7807 members plus {@code errorCode} and
 * {@code correlationId}), so a client parses one error format platform-wide.
 * It is built as a plain map rather than via {@code ProblemDetail}, so its
 * serialization does not depend on a Jackson mixin being registered.
 *
 * <p><b>Order.</b> Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 20: after
 * {@link CorrelationIdGlobalFilter} (so the correlation id header is already
 * on the request, and the response decorator that echoes it is in place) and
 * {@link UserContextPropagationGlobalFilter}, but before any route's
 * {@code modifyResponseBody}/{@code modifyRequestBody} filters — a rejected
 * request is turned away before anything starts buffering or rewriting its
 * body.
 */
public class ProblemDetailRateLimitFilter implements GatewayFilter, Ordered {

    static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";
    private static final String UNRESOLVED_KEY = "ip:unresolved";

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;
    private static final MediaType APPLICATION_PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final RedisRateLimiter rateLimiter;
    private final KeyResolver keyResolver;
    private final ObjectMapper objectMapper;
    private final long retryAfterSeconds;

    public ProblemDetailRateLimitFilter(RedisRateLimiter rateLimiter, KeyResolver keyResolver,
                                        ObjectMapper objectMapper, long retryAfterSeconds) {
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.objectMapper = objectMapper;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unrouted";
        return keyResolver.resolve(exchange)
                // The platform's resolver never completes empty; this keeps the
                // filter correct for any resolver (an empty key here would
                // otherwise complete the exchange with no response at all).
                .defaultIfEmpty(UNRESOLVED_KEY)
                .flatMap(key -> rateLimiter.isAllowed(routeId, key))
                .flatMap(response -> {
                    copyRateLimitHeaders(response, exchange.getResponse());
                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }
                    return reject(exchange);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static void copyRateLimitHeaders(RateLimiter.Response response, ServerHttpResponse httpResponse) {
        response.getHeaders().forEach((name, value) -> httpResponse.getHeaders().add(name, value));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        byte[] body = problemBody(exchange);
        response.getHeaders().setContentLength(body.length);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] problemBody(ServerWebExchange exchange) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", "Rate limit exceeded. Retry after %d second(s).".formatted(retryAfterSeconds));
        problem.put("instance", exchange.getRequest().getPath().value());
        problem.put("errorCode", ERROR_CODE);
        problem.put("correlationId",
                exchange.getRequest().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME));
        try {
            return objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException ex) {
            // A map of strings and an int cannot fail to serialize; if it ever
            // did, a minimal hand-written body still keeps the 429 well-formed.
            return "{\"status\":429,\"title\":\"Too Many Requests\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
