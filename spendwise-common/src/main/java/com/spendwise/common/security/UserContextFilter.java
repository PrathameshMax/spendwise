package com.spendwise.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Verifies the signed {@value UserContextConstants#HEADER_NAME} header the API
 * Gateway attaches to every request it forwards downstream (Milestone 6), so this
 * service can trust the caller's identity without decoding/validating the JWT
 * itself — only the Gateway holds the JWKS-based verification logic.
 *
 * The header is not a JWT: it is a lightweight
 * {@code base64url(payload).base64url(HMAC-SHA256(payload))} envelope over
 * {@code {userId, email}}, signed with a secret shared only between the Gateway
 * and the business services ({@code spendwise.security.user-context-secret}).
 *
 * A missing or invalid header does not reject the request at this milestone —
 * enforcement (rejecting unauthenticated calls, role/claim checks) is a
 * documented later scope, not part of Milestone 6's stated implementation; this
 * filter's only job is to make a verified identity available via
 * {@link UserContextHolder} when one is present.
 *
 * <p>The actual HMAC verification/parsing (Milestone 15) now lives in the
 * shared {@link UserContextVerifier}, reused as-is by {@link ReactiveUserContextFilter}.
 */
public class UserContextFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private final String sharedSecret;

    public UserContextFilter(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader(UserContextConstants.HEADER_NAME);
        if (header != null && !header.isBlank()) {
            try {
                UserContextHolder.set(UserContextVerifier.verifyAndParse(header, sharedSecret));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.debug("Discarding invalid {} header: {}", UserContextConstants.HEADER_NAME, e.getMessage());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}
