package com.spendwise.common.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Claim checks every service applies after the RS256 signature is verified.
 *
 * <ol>
 *   <li>{@code exp}/{@code nbf} with clock skew — and {@code exp} must exist
 *       ({@link JwtTimestampValidator} silently passes a token without one).</li>
 *   <li>{@code iss} equals the Keycloak realm.</li>
 *   <li>{@code aud} contains this API's audience.</li>
 *   <li>{@code typ} is {@code Bearer} — Keycloak signs refresh and ID tokens with
 *       the same realm key, so the type check stops them being replayed as
 *       access tokens.</li>
 * </ol>
 */
public final class SpendwiseJwtValidators {

    public static final String TOKEN_TYPE_CLAIM = "typ";

    private SpendwiseJwtValidators() {
    }

    public static OAuth2TokenValidator<Jwt> accessToken(SpendwiseResourceServerProperties properties) {
        String audience = properties.getAudience();
        String acceptedTokenType = properties.getAcceptedTokenType();
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                new JwtTimestampValidator(properties.getClockSkew()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtIssuerValidator(properties.getIssuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>(TOKEN_TYPE_CLAIM, acceptedTokenType::equals));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
