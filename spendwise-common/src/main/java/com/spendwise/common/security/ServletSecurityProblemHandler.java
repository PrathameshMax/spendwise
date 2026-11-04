package com.spendwise.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.tracing.CorrelationIdConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RFC 7807 bodies for the two security outcomes every servlet service returns:
 * <ul>
 *   <li>401 — no token, or token invalid (signature, expiry, issuer, audience, type).</li>
 *   <li>403 — valid token, but missing the role/scope the endpoint requires.</li>
 * </ul>
 * Spring's bearer entry point / access-denied handler set the RFC 6750
 * {@code WWW-Authenticate} header and status; this class adds the body and the
 * security audit event. Spring Security's filter chain runs before the service's
 * CorrelationIdFilter, so the correlation id is read from the request header.
 */
public class ServletSecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    public ServletSecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String correlationId = correlationId(request);
        SecurityAuditLogger.authenticationFailed(request.getMethod(), request.getRequestURI(),
                reason(exception), correlationId);
        bearerEntryPoint.commence(request, response, exception);
        int status = response.getStatus();
        String errorCode = status == HttpStatus.BAD_REQUEST.value() ? "INVALID_TOKEN_REQUEST" : "UNAUTHENTICATED";
        write(response, status, errorCode, "A valid bearer access token is required.", request, correlationId);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        String correlationId = correlationId(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityAuditLogger.accessDenied(request.getMethod(), request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName(), correlationId);
        bearerAccessDeniedHandler.handle(request, response, exception);
        write(response, HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED",
                "You do not have permission to perform this operation.", request, correlationId);
    }

    private void write(HttpServletResponse response, int status, String errorCode, String detail,
                       HttpServletRequest request, String correlationId) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", HttpStatus.valueOf(status).getReasonPhrase());
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("errorCode", errorCode);
        problem.put("correlationId", correlationId);
        problem.put("timestamp", Instant.now().toString());
        response.setStatus(status);
        response.setHeader(CorrelationIdConstants.HEADER_NAME, correlationId);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String reason(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError().getDescription() != null) {
            return oauth2Exception.getError().getDescription();
        }
        return exception.getMessage();
    }

    private static String correlationId(HttpServletRequest request) {
        String fromMdc = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        String inbound = request.getHeader(CorrelationIdConstants.HEADER_NAME);
        if (inbound != null && SAFE_CORRELATION_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
