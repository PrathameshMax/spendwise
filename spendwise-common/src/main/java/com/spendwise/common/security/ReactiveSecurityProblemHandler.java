package com.spendwise.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.tracing.CorrelationIdConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * WebFlux twin of {@link ServletSecurityProblemHandler} for analytics-service:
 * RFC 7807 401/403 with RFC 6750 {@code WWW-Authenticate} and a security audit event.
 */
public class ReactiveSecurityProblemHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final ObjectMapper objectMapper;

    public ReactiveSecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = correlationId(request);
        String reason = exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError().getDescription() != null
                ? oauth2Exception.getError().getDescription()
                : exception.getMessage();
        SecurityAuditLogger.authenticationFailed(request.getMethod().name(), request.getPath().value(),
                reason, correlationId);
        String wwwAuthenticate = exception instanceof OAuth2AuthenticationException
                ? "Bearer error=\"invalid_token\""
                : "Bearer";
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "A valid bearer access token is required.", wwwAuthenticate, correlationId);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = correlationId(request);
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .flatMap(subject -> {
                    SecurityAuditLogger.accessDenied(request.getMethod().name(), request.getPath().value(),
                            subject, correlationId);
                    return write(exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                            "You do not have permission to perform this operation.",
                            "Bearer error=\"insufficient_scope\"", correlationId);
                });
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String detail,
                             String wwwAuthenticate, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", exchange.getRequest().getPath().value());
        problem.put("errorCode", errorCode);
        problem.put("correlationId", correlationId);
        problem.put("timestamp", Instant.now().toString());
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            body = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }
        response.setStatusCode(status);
        HttpHeaders headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(CorrelationIdConstants.HEADER_NAME, correlationId);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private static String correlationId(ServerHttpRequest request) {
        String inbound = request.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (inbound != null && SAFE_CORRELATION_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
