package com.spendwise.apigateway.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SpendWise-specific claim policy, run after signature, exp/nbf, issuer and audience checks.
 *
 * <ul>
 *   <li>{@code typ} must equal the accepted type ({@code Bearer} for a Keycloak
 *       access token) — Keycloak signs refresh and ID tokens with the same realm
 *       key, so without this check they would be valid bearer credentials.</li>
 *   <li>{@code exp} and {@code iat} must be present ({@code JwtTimestampValidator}
 *       silently passes a token without {@code exp}); {@code iat} must not be in
 *       the future beyond the clock skew.</li>
 *   <li>{@code exp - iat} must not exceed the maximum lifetime — defence in depth
 *       against long-lived tokens even if {@code typ} handling regresses.</li>
 *   <li>{@code sub} must be a canonical UUID (the owner id every service scopes
 *       its queries by) and {@code email} present (user tokens only reach the
 *       gateway; service-account tokens never do).</li>
 * </ul>
 */
public final class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_EMAIL = "email";

    private final String acceptedTokenType;
    private final Duration clockSkew;
    private final Duration maxTokenLifetime;
    private final Clock clock;

    public AccessTokenClaimsValidator(String acceptedTokenType,
                                      Duration clockSkew,
                                      Duration maxTokenLifetime,
                                      Clock clock) {
        if (!StringUtils.hasText(acceptedTokenType)) {
            throw new IllegalArgumentException("acceptedTokenType must not be blank");
        }
        this.acceptedTokenType = acceptedTokenType;
        this.clockSkew = Objects.requireNonNull(clockSkew, "clockSkew");
        this.maxTokenLifetime = Objects.requireNonNull(maxTokenLifetime, "maxTokenLifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Full acceptance chain, in evaluation order. Every error carries a
     * {@link JwtRejectionReason} code; the first one classifies the rejection.
     */
    public static OAuth2TokenValidator<Jwt> withStandardChecks(String issuer,
                                                              String audience,
                                                              String acceptedTokenType,
                                                              Duration clockSkew,
                                                              Duration maxTokenLifetime,
                                                              Clock clock) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(clockSkew);
        timestampValidator.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                JwtRejectionReason.TOKEN_EXPIRED.tag(timestampValidator),
                JwtRejectionReason.ISSUER_MISMATCH.tag(new JwtIssuerValidator(issuer)),
                JwtRejectionReason.AUDIENCE_MISMATCH.tag(new JwtClaimValidator<Collection<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))),
                new AccessTokenClaimsValidator(acceptedTokenType, clockSkew, maxTokenLifetime, clock));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<OAuth2Error> errors = new ArrayList<>();

        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!acceptedTokenType.equals(tokenType)) {
            errors.add(JwtRejectionReason.TOKEN_TYPE_REJECTED.error(
                    "token type '" + tokenType + "' is not accepted, expected '" + acceptedTokenType + "'"));
        }

        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error("exp claim is required"));
        }
        if (issuedAt == null) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error("iat claim is required"));
        } else if (issuedAt.isAfter(clock.instant().plus(clockSkew))) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error("iat claim " + issuedAt + " is in the future"));
        }
        if (issuedAt != null && expiresAt != null
                && Duration.between(issuedAt, expiresAt).compareTo(maxTokenLifetime) > 0) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error(
                    "token lifetime exceeds the maximum of " + maxTokenLifetime.toSeconds() + "s"));
        }

        if (!isCanonicalUuid(jwt.getSubject())) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error("sub claim must be a UUID"));
        }
        if (!StringUtils.hasText(jwt.getClaimAsString(CLAIM_EMAIL))) {
            errors.add(JwtRejectionReason.CLAIMS_INVALID.error("email claim is required"));
        }

        return errors.isEmpty() ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(errors);
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
