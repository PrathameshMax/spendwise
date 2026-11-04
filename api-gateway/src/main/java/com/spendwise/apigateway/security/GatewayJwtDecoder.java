package com.spendwise.apigateway.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Translates "signing keys could not be obtained" into a non-bad
 * {@link JwtException}.
 *
 * <p>{@code NimbusReactiveJwtDecoder} wraps any JWK source failure in a raw
 * {@link IllegalStateException}. {@code JwtReactiveAuthenticationManager} only
 * maps {@link JwtException}, so without this translation a JWKS outage escapes
 * the security filter as an unhandled error (generic 500). Mapped here, it
 * becomes an {@code AuthenticationServiceException} that
 * {@link AuthenticationProblemHandler} answers with 503 + Retry-After, keeping
 * "your token is bad" (401) distinct from "we cannot verify right now" (503).
 */
public class GatewayJwtDecoder implements ReactiveJwtDecoder {

    private final ReactiveJwtDecoder delegate;

    public GatewayJwtDecoder(ReactiveJwtDecoder delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * RS256-only Nimbus decoder fed by {@link JwksKeyCache}, with the given
     * validator chain, wrapped in the key-failure translation.
     */
    public static GatewayJwtDecoder create(JwksKeyCache keyCache, OAuth2TokenValidator<Jwt> validator) {
        NimbusReactiveJwtDecoder nimbusDecoder = NimbusReactiveJwtDecoder.withJwkSource(keyCache::keysFor)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        nimbusDecoder.setJwtValidator(validator);
        return new GatewayJwtDecoder(nimbusDecoder);
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        return Mono.defer(() -> delegate.decode(token))
                .onErrorMap(IllegalStateException.class, error -> new SigningKeysUnavailableException(
                        "Signing keys could not be obtained from the JWK Set endpoint", error));
    }

    public static final class SigningKeysUnavailableException extends JwtException {

        public SigningKeysUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
