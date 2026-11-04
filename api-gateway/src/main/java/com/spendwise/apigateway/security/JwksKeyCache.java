package com.spendwise.apigateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-blocking cache of auth-service's public signing keys, used as the JWK
 * source of the Gateway's {@code NimbusReactiveJwtDecoder}.
 *
 * <ul>
 *   <li><b>Scheduled refresh</b> — fetched at startup and every
 *       {@code refreshInterval}, so key rotation is picked up without traffic.</li>
 *   <li><b>Single-flight</b> — concurrent refreshes share one HTTP call.</li>
 *   <li><b>Outage tolerance</b> — a failed refresh keeps serving the last good
 *       key set; only a cold cache with an unreachable JWKS surfaces as an error
 *       (mapped to 503 by {@link GatewayJwtDecoder}).</li>
 *   <li><b>Unknown-kid refresh is rate limited</b> — tokens carrying a kid not in
 *       the cache trigger at most one refresh per {@code unknownKidCooldown}, so
 *       forged kids cannot turn the Gateway into a JWKS request amplifier
 *       against auth-service (Spring's default remote source re-fetches on every
 *       kid miss, and the rate limiter runs only after authentication).</li>
 *   <li><b>Early rejection</b> — non-RS256 or kid-less headers never cause a fetch.</li>
 * </ul>
 */
public class JwksKeyCache implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyCache.class);

    private static final String REFRESH_METRIC = "spendwise.gateway.jwks.refresh";
    private static final String STALENESS_METRIC = "spendwise.gateway.jwks.staleness";
    private static final String TRIGGER_SCHEDULED = "scheduled";
    private static final String TRIGGER_COLD_START = "cold_start";
    private static final String TRIGGER_UNKNOWN_KID = "unknown_kid";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";
    private static final String OUTCOME_THROTTLED = "throttled";

    private final WebClient webClient;
    private final String jwkSetUri;
    private final Duration refreshInterval;
    private final Duration unknownKidCooldown;
    private final Duration fetchTimeout;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final AtomicReference<KeySetSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<RefreshAttempt> inflight = new AtomicReference<>();
    private final AtomicReference<Instant> lastUnknownKidRefresh = new AtomicReference<>(Instant.EPOCH);
    private volatile Disposable scheduledRefresh;

    public JwksKeyCache(WebClient webClient,
                        String jwkSetUri,
                        Duration refreshInterval,
                        Duration unknownKidCooldown,
                        Duration fetchTimeout,
                        MeterRegistry meterRegistry,
                        Clock clock) {
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.jwkSetUri = Objects.requireNonNull(jwkSetUri, "spendwise.security.jwk-set-uri must be configured");
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval");
        this.unknownKidCooldown = Objects.requireNonNull(unknownKidCooldown, "unknownKidCooldown");
        this.fetchTimeout = Objects.requireNonNull(fetchTimeout, "fetchTimeout");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        Gauge.builder(STALENESS_METRIC, this, JwksKeyCache::stalenessSeconds)
                .description("Seconds since the Gateway last refreshed auth-service's JWK Set successfully")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    /**
     * JWK source for {@code NimbusReactiveJwtDecoder.withJwkSource(...)}: returns
     * the keys matching the token's header, or empty (→ 401) when none match.
     */
    public Flux<JWK> keysFor(SignedJWT jwt) {
        JWSHeader header = jwt.getHeader();
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID() == null) {
            return Flux.empty();
        }
        JWKSelector selector = new JWKSelector(JWKMatcher.forJWSHeader(header));

        KeySetSnapshot current = snapshot.get();
        if (current == null) {
            return refresh(TRIGGER_COLD_START).flatMapIterable(selector::select);
        }
        List<JWK> matches = selector.select(current.keySet());
        if (!matches.isEmpty()) {
            return Flux.fromIterable(matches);
        }
        return refreshForUnknownKid()
                .flatMapIterable(selector::select)
                .onErrorResume(error -> Flux.empty());
    }

    private Mono<JWKSet> refreshForUnknownKid() {
        RefreshAttempt running = inflight.get();
        if (running != null) {
            return running.result();
        }
        Instant now = clock.instant();
        Instant last = lastUnknownKidRefresh.get();
        if (now.isBefore(last.plus(unknownKidCooldown)) || !lastUnknownKidRefresh.compareAndSet(last, now)) {
            meterRegistry.counter(REFRESH_METRIC, "trigger", TRIGGER_UNKNOWN_KID, "outcome", OUTCOME_THROTTLED)
                    .increment();
            return Mono.empty();
        }
        return refresh(TRIGGER_UNKNOWN_KID);
    }

    private Mono<JWKSet> refresh(String trigger) {
        return Mono.defer(() -> {
            RefreshAttempt running = inflight.get();
            if (running != null) {
                return running.result();
            }
            RefreshAttempt candidate = new RefreshAttempt(trigger);
            RefreshAttempt winner = inflight.compareAndExchange(null, candidate);
            return winner == null ? candidate.result() : winner.result();
        });
    }

    private Mono<JWKSet> fetch() {
        return webClient.get()
                .uri(jwkSetUri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(fetchTimeout)
                .switchIfEmpty(Mono.error(new IllegalStateException("JWK Set endpoint returned an empty body")))
                .map(JwksKeyCache::parsePublicKeys);
    }

    private static JWKSet parsePublicKeys(String body) {
        try {
            JWKSet keySet = JWKSet.parse(body).toPublicJWKSet();
            if (keySet.getKeys().isEmpty()) {
                throw new IllegalStateException("JWK Set contains no public keys");
            }
            return keySet;
        } catch (ParseException e) {
            throw new IllegalStateException("JWK Set response is not a valid JSON Web Key Set", e);
        }
    }

    private void onRefreshSuccess(String trigger, JWKSet keySet) {
        KeySetSnapshot previous = snapshot.getAndSet(new KeySetSnapshot(keySet, clock.instant()));
        meterRegistry.counter(REFRESH_METRIC, "trigger", trigger, "outcome", OUTCOME_SUCCESS).increment();
        List<String> keyIds = keyIds(keySet);
        if (previous == null || !keyIds(previous.keySet()).equals(keyIds)) {
            log.info("jwks_keys_loaded trigger={} keyIds={} uri={}", trigger, keyIds, jwkSetUri);
        } else {
            log.debug("jwks_refreshed_unchanged trigger={} keyIds={}", trigger, keyIds);
        }
    }

    private void onRefreshFailure(String trigger, Throwable error) {
        meterRegistry.counter(REFRESH_METRIC, "trigger", trigger, "outcome", OUTCOME_FAILURE).increment();
        KeySetSnapshot current = snapshot.get();
        if (current == null) {
            log.error("jwks_refresh_failed trigger={} cachedKeys=none uri={} cause={}", trigger, jwkSetUri,
                    error.toString());
        } else {
            log.warn("jwks_refresh_failed trigger={} servingCachedKeys={} cachedAgeSeconds={} uri={} cause={}",
                    trigger, keyIds(current.keySet()), Duration.between(current.fetchedAt(), clock.instant()).toSeconds(),
                    jwkSetUri, error.toString());
        }
    }

    private static List<String> keyIds(JWKSet keySet) {
        return keySet.getKeys().stream()
                .map(JWK::getKeyID)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private double stalenessSeconds() {
        KeySetSnapshot current = snapshot.get();
        if (current == null) {
            return Double.NaN;
        }
        return Duration.between(current.fetchedAt(), clock.instant()).toMillis() / 1000.0;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        scheduledRefresh = Flux.interval(Duration.ZERO, refreshInterval)
                .onBackpressureDrop()
                .concatMap(tick -> refresh(TRIGGER_SCHEDULED).onErrorResume(error -> Mono.empty()))
                .subscribe();
    }

    @Override
    public synchronized void stop() {
        Disposable current = scheduledRefresh;
        if (current != null) {
            current.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        Disposable current = scheduledRefresh;
        return current != null && !current.isDisposed();
    }

    private record KeySetSnapshot(JWKSet keySet, Instant fetchedAt) {
    }

    private final class RefreshAttempt {

        private final Mono<JWKSet> result;

        private RefreshAttempt(String trigger) {
            this.result = fetch()
                    .doOnNext(keySet -> onRefreshSuccess(trigger, keySet))
                    .doOnError(error -> onRefreshFailure(trigger, error))
                    .doFinally(signal -> inflight.compareAndSet(this, null))
                    .cache();
        }

        private Mono<JWKSet> result() {
            return result;
        }
    }
}
