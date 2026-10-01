package com.spendwise.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Milestone 15 — the signed {@code X-User-Context} header verification/parsing
 * logic, extracted from {@link UserContextFilter} (Milestone 6) into this shared
 * static helper so it has exactly one implementation rather than two: the new
 * WebFlux-based {@link ReactiveUserContextFilter} needs the identical
 * HMAC-SHA256-verify-then-deserialize logic, and security-sensitive signature
 * checking is exactly the kind of code that must never exist as two
 * independently-maintained copies that could silently drift apart.
 */
public final class UserContextVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UserContextVerifier() {
    }

    public static UserContext verifyAndParse(String header, String sharedSecret) throws GeneralSecurityException {
        int separator = header.indexOf('.');
        if (separator < 0) {
            throw new IllegalArgumentException("malformed user-context header");
        }
        String encodedPayload = header.substring(0, separator);
        String encodedSignature = header.substring(separator + 1);

        byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
        byte[] expectedSignature = sign(payloadBytes, sharedSecret);
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

    private static byte[] sign(byte[] payloadBytes, String sharedSecret) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(payloadBytes);
    }
}
