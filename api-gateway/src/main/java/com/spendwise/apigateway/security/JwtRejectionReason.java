package com.spendwise.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Closed set of reasons the Gateway rejects a request at authentication time.
 * {@link #code()} is the low-cardinality metric tag and the {@link OAuth2Error}
 * code carried through the validator chain; {@link #name()} is the RFC 7807
 * {@code errorCode}; {@link #clientDetail()} is the only text returned to the
 * caller (internal validation detail stays in the logs).
 */
public enum JwtRejectionReason {

    TOKEN_MISSING("token_missing", HttpStatus.UNAUTHORIZED,
            "A bearer access token is required."),
    BEARER_REQUEST_INVALID("bearer_request_invalid", HttpStatus.BAD_REQUEST,
            "The Authorization header is not a valid bearer token request."),
    TOKEN_UNVERIFIABLE("token_unverifiable", HttpStatus.UNAUTHORIZED,
            "The access token is malformed or its signature could not be verified."),
    TOKEN_EXPIRED("token_expired", HttpStatus.UNAUTHORIZED,
            "The access token is expired or not yet valid."),
    ISSUER_MISMATCH("issuer_mismatch", HttpStatus.UNAUTHORIZED,
            "The access token was not issued by a trusted issuer."),
    AUDIENCE_MISMATCH("audience_mismatch", HttpStatus.UNAUTHORIZED,
            "The access token was not issued for this API."),
    TOKEN_TYPE_REJECTED("token_type_rejected", HttpStatus.UNAUTHORIZED,
            "Only access tokens are accepted."),
    CLAIMS_INVALID("claims_invalid", HttpStatus.UNAUTHORIZED,
            "The access token does not carry the required claims."),
    VERIFICATION_UNAVAILABLE("verification_unavailable", HttpStatus.SERVICE_UNAVAILABLE,
            "Token verification is temporarily unavailable. Retry later.");

    private final String code;
    private final HttpStatus status;
    private final String clientDetail;

    JwtRejectionReason(String code, HttpStatus status, String clientDetail) {
        this.code = code;
        this.status = status;
        this.clientDetail = clientDetail;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String clientDetail() {
        return clientDetail;
    }

    public OAuth2Error error(String internalDescription) {
        return new OAuth2Error(code, internalDescription, null);
    }

    /**
     * Re-labels every error a framework validator produces with this reason's
     * code, so the rejection can be classified without parsing descriptions.
     */
    public OAuth2TokenValidator<Jwt> tag(OAuth2TokenValidator<Jwt> delegate) {
        return token -> {
            OAuth2TokenValidatorResult result = delegate.validate(token);
            if (!result.hasErrors()) {
                return result;
            }
            List<OAuth2Error> tagged = result.getErrors().stream()
                    .map(original -> new OAuth2Error(code, original.getDescription(), original.getUri()))
                    .toList();
            return OAuth2TokenValidatorResult.failure(tagged);
        };
    }

    public static Optional<JwtRejectionReason> fromCode(String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.code.equals(code))
                .findFirst();
    }
}
