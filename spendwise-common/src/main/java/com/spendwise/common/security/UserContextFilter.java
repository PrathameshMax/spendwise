package com.spendwise.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

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
 */
public class UserContextFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                UserContextHolder.set(verifyAndParse(header));
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

    private UserContext verifyAndParse(String header) throws GeneralSecurityException {
        int separator = header.indexOf('.');
        if (separator < 0) {
            throw new IllegalArgumentException("malformed user-context header");
        }
        String encodedPayload = header.substring(0, separator);
        String encodedSignature = header.substring(separator + 1);

        byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
        byte[] expectedSignature = sign(payloadBytes);
        byte[] providedSignature = Base64.getUrlDecoder().decode(encodedSignature);

        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new IllegalArgumentException("signature mismatch");
        }

        try {
            return MAPPER.readValue(payloadBytes, UserContext.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("unparseable user-context payload", e);
        }
    }

    private byte[] sign(byte[] payloadBytes) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(payloadBytes);
    }
}
