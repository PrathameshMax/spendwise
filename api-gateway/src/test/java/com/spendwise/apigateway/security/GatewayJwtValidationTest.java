package com.spendwise.apigateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Exercises the exact production chain without a Spring context:
 * JwtReactiveAuthenticationManager → GatewayJwtDecoder → Nimbus (RS256) →
 * JwksKeyCache → validator chain → AuthenticationProblemHandler.classify.
 */
class GatewayJwtValidationTest {

    private static final String ISSUER = "http://localhost:8180/realms/spendwise";
    private static final String AUDIENCE = "spendwise-api";
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    private static final String SUBJECT = UUID.randomUUID().toString();

    private volatile boolean jwksAvailable;
    private RSAKey trustedKey;
    private RSAKey untrustedKey;
    private JwtReactiveAuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() throws JOSEException {
        trustedKey = new RSAKeyGenerator(2048).keyID("trusted").generate();
        untrustedKey = new RSAKeyGenerator(2048).keyID("untrusted").generate();
        String jwksBody = new JWKSet(trustedKey.toPublicJWK()).toString();
        jwksAvailable = true;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    if (!jwksAvailable) {
                        return Mono.error(new ConnectException("Connection refused: auth-service/8081"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(jwksBody)
                            .build());
                })
                .build();
        JwksKeyCache keyCache = new JwksKeyCache(webClient, "http://auth-service:8081/oauth2/jwks",
                Duration.ofMinutes(5), Duration.ofSeconds(15), Duration.ofSeconds(3), new SimpleMeterRegistry(), clock);

        GatewayJwtDecoder decoder = GatewayJwtDecoder.create(keyCache, AccessTokenClaimsValidator.withStandardChecks(
                ISSUER, AUDIENCE, "Bearer", Duration.ofSeconds(30), Duration.ofHours(1), clock));
        authenticationManager = new JwtReactiveAuthenticationManager(decoder);
    }

    @Test
    void validAccessTokenAuthenticatesWithSubjectAsPrincipal() throws JOSEException {
        String token = sign(trustedKey, claims("Bearer", ISSUER, NOW.minusSeconds(60), Duration.ofMinutes(5))
                .claim("email", "user@spendwise.dev").build());

        Authentication authentication = authenticationManager.authenticate(new BearerTokenAuthenticationToken(token)).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(SUBJECT);
    }

    @Test
    void keycloakRefreshTokenIsRejectedAsWrongTokenType() throws JOSEException {
        String token = sign(trustedKey, claims("Refresh", ISSUER, NOW.minusSeconds(60), Duration.ofMinutes(30))
                .claim("email", "user@spendwise.dev").jwtID(UUID.randomUUID().toString()).build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.TOKEN_TYPE_REJECTED);
    }

    @Test
    void expiredTokenIsRejected() throws JOSEException {
        String token = sign(trustedKey, claims("Bearer", ISSUER, NOW.minus(Duration.ofHours(2)), Duration.ofMinutes(5))
                .claim("email", "user@spendwise.dev").build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.TOKEN_EXPIRED);
    }

    @Test
    void foreignIssuerIsRejected() throws JOSEException {
        String token = sign(trustedKey, claims("Bearer", "https://idp.attacker.example", NOW.minusSeconds(60),
                Duration.ofMinutes(5)).claim("email", "user@spendwise.dev").build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.ISSUER_MISMATCH);
    }

    @Test
    void tokenMintedForAnotherApiIsRejected() throws JOSEException {
        String token = sign(trustedKey, new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(SUBJECT)
                .audience("some-other-api")
                .claim("typ", "Bearer")
                .claim("email", "user@spendwise.dev")
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(NOW.plusSeconds(240)))
                .build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.AUDIENCE_MISMATCH);
    }

    @Test
    void tokenWithoutEmailClaimIsRejected() throws JOSEException {
        String token = sign(trustedKey, claims("Bearer", ISSUER, NOW.minusSeconds(60), Duration.ofMinutes(5)).build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.CLAIMS_INVALID);
    }

    @Test
    void tokenSignedByUntrustedKeyIsUnverifiable() throws JOSEException {
        String token = sign(untrustedKey, claims("Bearer", ISSUER, NOW.minusSeconds(60), Duration.ofMinutes(5))
                .claim("email", "user@spendwise.dev").build());

        assertThat(rejectionOf(token)).isEqualTo(JwtRejectionReason.TOKEN_UNVERIFIABLE);
    }

    @Test
    void unreachableJwksOnColdStartMapsTo503() throws JOSEException {
        jwksAvailable = false;
        String token = sign(trustedKey, claims("Bearer", ISSUER, NOW.minusSeconds(60), Duration.ofMinutes(5))
                .claim("email", "user@spendwise.dev").build());

        JwtRejectionReason reason = rejectionOf(token);

        assertThat(reason).isEqualTo(JwtRejectionReason.VERIFICATION_UNAVAILABLE);
        assertThat(reason.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private JwtRejectionReason rejectionOf(String token) {
        Throwable failure = catchThrowable(
                () -> authenticationManager.authenticate(new BearerTokenAuthenticationToken(token)).block());
        assertThat(failure).isInstanceOf(AuthenticationException.class);
        return AuthenticationProblemHandler.classify((AuthenticationException) failure);
    }

    private static JWTClaimsSet.Builder claims(String tokenType, String issuer, Instant issuedAt, Duration ttl) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .audience(List.of(AUDIENCE, "account"))
                .claim("typ", tokenType)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(ttl)));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
