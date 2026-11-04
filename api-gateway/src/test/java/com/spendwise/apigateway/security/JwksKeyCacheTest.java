package com.spendwise.apigateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JwksKeyCacheTest {

    private static final Duration UNKNOWN_KID_COOLDOWN = Duration.ofSeconds(15);

    private final AtomicInteger fetchCount = new AtomicInteger();
    private volatile boolean jwksAvailable;
    private volatile String jwksBody;
    private MutableClock clock;
    private RSAKey keyOne;
    private RSAKey keyTwo;
    private JwksKeyCache cache;

    @BeforeEach
    void setUp() throws JOSEException {
        keyOne = new RSAKeyGenerator(2048).keyID("k1").generate();
        keyTwo = new RSAKeyGenerator(2048).keyID("k2").generate();
        jwksBody = new JWKSet(keyOne.toPublicJWK()).toString();
        jwksAvailable = true;
        clock = new MutableClock(Instant.parse("2026-11-04T08:00:00Z"));

        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    fetchCount.incrementAndGet();
                    if (!jwksAvailable) {
                        return Mono.error(new ConnectException("Connection refused: auth-service/8081"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(jwksBody)
                            .build());
                })
                .build();

        cache = new JwksKeyCache(webClient, "http://auth-service:8081/oauth2/jwks", Duration.ofMinutes(5),
                UNKNOWN_KID_COOLDOWN, Duration.ofSeconds(3), new SimpleMeterRegistry(), clock);
    }

    @Test
    void coldStartLoadsKeysOnceThenServesFromCache() {
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k1")))
                .assertNext(jwk -> assertThat(jwk.getKeyID()).isEqualTo("k1"))
                .verifyComplete();
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k1")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void unknownKidRefreshIsRateLimited() {
        cache.keysFor(token(JWSAlgorithm.RS256, "k1")).blockLast();

        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "forged-1"))).verifyComplete();
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "forged-2"))).verifyComplete();
        assertThat(fetchCount).hasValue(2);

        clock.advance(UNKNOWN_KID_COOLDOWN.plusSeconds(1));
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "forged-3"))).verifyComplete();
        assertThat(fetchCount).hasValue(3);
    }

    @Test
    void rotatedSigningKeyIsPickedUpOnFirstUnknownKid() {
        cache.keysFor(token(JWSAlgorithm.RS256, "k1")).blockLast();
        jwksBody = new JWKSet(keyTwo.toPublicJWK()).toString();

        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k2")))
                .assertNext(jwk -> assertThat(jwk.getKeyID()).isEqualTo("k2"))
                .verifyComplete();
    }

    @Test
    void keepsServingCachedKeysWhileJwksEndpointIsDown() {
        cache.keysFor(token(JWSAlgorithm.RS256, "k1")).blockLast();
        jwksAvailable = false;

        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k-unknown"))).verifyComplete();
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k1")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void coldStartWithUnreachableJwksSurfacesAsError() {
        jwksAvailable = false;

        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, "k1")))
                .expectError()
                .verify();
    }

    @Test
    void nonRs256AndKidlessTokensNeverTriggerAFetch() {
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.HS256, "k1"))).verifyComplete();
        StepVerifier.create(cache.keysFor(token(JWSAlgorithm.RS256, null))).verifyComplete();

        assertThat(fetchCount).hasValue(0);
    }

    private static SignedJWT token(JWSAlgorithm algorithm, String keyId) {
        JWSHeader header = new JWSHeader.Builder(algorithm).keyID(keyId).build();
        return new SignedJWT(header, new JWTClaimsSet.Builder().subject("subject").build());
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
