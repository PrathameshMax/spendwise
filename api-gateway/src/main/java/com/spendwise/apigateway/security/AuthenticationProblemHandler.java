package com.spendwise.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.tracing.CorrelationIdConstants;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single writer for every authentication rejection at the edge.
 *
 * <ul>
 *   <li>As {@link ServerAuthenticationEntryPoint}: anonymous request to a
 *       protected route → 401 {@code TOKEN_MISSING}.</li>
 *   <li>As {@link ServerAuthenticationFailureHandler}: bearer token present but
 *       rejected → 401/400 with the classified reason, or 503 + Retry-After when
 *       verification itself is unavailable. Replaces the default
 *       {@code ServerAuthenticationEntryPointFailureHandler}, which re-throws
 *       {@code AuthenticationServiceException} as a generic 500.</li>
 * </ul>
 *
 * Security filters run before Gateway global filters, so
 * {@code CorrelationIdGlobalFilter} has not executed yet: the correlation id is
 * resolved here (validated inbound value or a new UUID) and returned in the
 * header and body so a rejected call can still be traced in Kibana/Splunk.
 * The token value is never logged.
 */
public class AuthenticationProblemHandler implements ServerAuthenticationEntryPoint, ServerAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationProblemHandler.class);

    private static final String REJECTION_METRIC = "spendwise.gateway.auth.rejections";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String realm;
    private final Duration retryAfter;
    private final Clock clock;

    public AuthenticationProblemHandler(ObjectMapper objectMapper,
                                        MeterRegistry meterRegistry,
                                        String realm,
                                        Duration retryAfter,
                                        Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.realm = Objects.requireNonNull(realm, "realm");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return reject(exchange, exception);
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        return reject(webFilterExchange.getExchange(), exception);
    }

    static JwtRejectionReason classify(AuthenticationException exception) {
        if (exception instanceof AuthenticationServiceException) {
            return JwtRejectionReason.VERIFICATION_UNAVAILABLE;
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            if (oauth2Exception.getCause() instanceof JwtValidationException validationException) {
                return validationException.getErrors().stream()
                        .map(OAuth2Error::getErrorCode)
                        .map(JwtRejectionReason::fromCode)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .orElse(JwtRejectionReason.TOKEN_UNVERIFIABLE);
            }
            if (oauth2Exception.getError() instanceof BearerTokenError bearerTokenError
                    && bearerTokenError.getHttpStatus() == HttpStatus.BAD_REQUEST) {
                return JwtRejectionReason.BEARER_REQUEST_INVALID;
            }
            return JwtRejectionReason.TOKEN_UNVERIFIABLE;
        }
        return JwtRejectionReason.TOKEN_MISSING;
    }

    private Mono<Void> reject(ServerWebExchange exchange, AuthenticationException exception) {
        JwtRejectionReason reason = classify(exception);
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = resolveCorrelationId(request);
        String method = request.getMethod().name();
        String path = request.getPath().value();

        logRejection(reason, exception, method, path, correlationId);
        meterRegistry.counter(REJECTION_METRIC,
                "reason", reason.code(),
                "status", String.valueOf(reason.status().value())).increment();

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(reason.status());
        HttpHeaders headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl(CacheControl.noStore());
        headers.set(CorrelationIdConstants.HEADER_NAME, correlationId);
        if (reason.status() == HttpStatus.SERVICE_UNAVAILABLE) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds()));
        } else {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate(reason));
        }

        byte[] body = serialize(problem(reason, path, correlationId));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private String wwwAuthenticate(JwtRejectionReason reason) {
        StringBuilder value = new StringBuilder("Bearer realm=\"").append(realm).append('"');
        if (reason == JwtRejectionReason.TOKEN_MISSING) {
            return value.toString();
        }
        String errorCode = reason == JwtRejectionReason.BEARER_REQUEST_INVALID
                ? BearerTokenErrorCodes.INVALID_REQUEST
                : BearerTokenErrorCodes.INVALID_TOKEN;
        return value.append(", error=\"").append(errorCode)
                .append("\", error_description=\"").append(reason.clientDetail()).append('"')
                .toString();
    }

    private Map<String, Object> problem(JwtRejectionReason reason, String path, String correlationId) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", reason.status().getReasonPhrase());
        problem.put("status", reason.status().value());
        problem.put("detail", reason.clientDetail());
        problem.put("instance", path);
        problem.put("errorCode", reason.name());
        problem.put("correlationId", correlationId);
        problem.put("timestamp", clock.instant().toString());
        return problem;
    }

    private byte[] serialize(Map<String, Object> problem) {
        try {
            return objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            return ("{\"status\":" + problem.get("status") + "}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String resolveCorrelationId(ServerHttpRequest request) {
        String inbound = request.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (inbound != null && SAFE_CORRELATION_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }

    private static void logRejection(JwtRejectionReason reason,
                                     AuthenticationException exception,
                                     String method,
                                     String path,
                                     String correlationId) {
        switch (reason) {
            case VERIFICATION_UNAVAILABLE -> log.error(
                    "jwt_verification_unavailable method={} path={} correlationId={} cause={}",
                    method, path, correlationId, rootCause(exception));
            case TOKEN_MISSING -> log.debug(
                    "jwt_missing method={} path={} correlationId={}", method, path, correlationId);
            default -> log.warn(
                    "jwt_rejected reason={} method={} path={} correlationId={} detail={}",
                    reason.code(), method, path, correlationId, internalDetail(exception));
        }
    }

    private static String internalDetail(AuthenticationException exception) {
        if (exception.getCause() instanceof JwtValidationException validationException) {
            return validationException.getErrors().stream()
                    .map(OAuth2Error::getDescription)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("; "));
        }
        return exception.getMessage();
    }

    private static String rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
