package com.spendwise.transactionservice.client;

import com.spendwise.common.exception.DownstreamServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Milestone 10 — transaction-service's own Feign contract against
 * user-service's existing {@code GET /api/v1/users/{id}} endpoint. The
 * {@code name} attribute is user-service's {@code spring.application.name}
 * (its Eureka registration id, not a hostname): Spring Cloud OpenFeign
 * resolves it against the registry {@code discovery-server} maintains and
 * client-side load-balances across whatever instances are currently
 * registered under it, replacing what would otherwise be a hardcoded
 * {@code http://user-service:8082} URL (Q41/Q42).
 *
 * <p>Declared here rather than in spendwise-common because this contract is
 * business-domain-specific (it names a concrete service and a concrete
 * endpoint) — spendwise-common carries zero business logic, so
 * budget-service declares its own identical-looking copy of this interface
 * rather than sharing this one. Milestone 12's {@code @CircuitBreaker} below
 * is applied <b>only</b> here, not on budget-service's copy — the roadmap
 * scopes this milestone explicitly to "the Transaction→User cross-service
 * call," not both consumers; budget-service keeps Milestone 11's
 * Retry-with-timeouts protection only.
 *
 * <p>{@code @Retry(name = "userServiceLookup")} (Milestone 11) is placed on
 * this interface method, not on the {@code verifyUserExists} call site in
 * {@code TransactionService} — Resilience4j's annotation support is
 * Spring-AOP-proxy-based, so it only intercepts a call that arrives from
 * outside the bean; {@code verifyUserExists} calls this method externally
 * (crossing the Feign client bean's proxy boundary), which is exactly what
 * makes the annotation effective here.
 *
 * <p>Stacking order matters (Milestone 13 digs into this further):
 * Resilience4j's default aspect order makes {@code @Retry} the <b>outer</b>
 * layer and {@code @CircuitBreaker} the <b>inner</b> one — each of Retry's
 * attempts makes its own fresh pass through the CircuitBreaker. That is why
 * {@code fallbackMethod} is declared on {@code @Retry}, not on
 * {@code @CircuitBreaker}: a fallback attached to the inner annotation would
 * fire on every single attempt and silently swallow Retry's remaining
 * attempts before they ever ran. Attached to the outer one instead, the
 * fallback only fires once, after the whole Retry+CircuitBreaker chain has
 * had its say — whether that means "all retries exhausted" or "the circuit
 * is already OPEN and rejected the call outright with
 * {@code CallNotPermittedException}" (Q46).
 *
 * <p>Both the retry policy (config-repo/application.yml) and the circuit
 * breaker policy (this service's own application.yml) exclude
 * {@code feign.FeignException$NotFound} via their respective
 * {@code ignore-exceptions} — this keeps a 404 out of the retry decision
 * (it is never retried) and out of the circuit's failure-rate sliding
 * window (it is never counted as a failure). It does <b>not</b>, by itself,
 * keep a 404 out of {@link #getByIdFallback}: Resilience4j-Spring's
 * fallback-routing wrapper invokes the configured {@code fallbackMethod} for
 * <i>any</i> exception that propagates out of the decorated call, regardless
 * of {@code ignore-exceptions} — that config only governs the Retry/
 * CircuitBreaker core logic, not the separate AOP layer that dispatches to a
 * fallback. Left alone, a perfectly healthy user-service answering "no such
 * user" would still reach the fallback and get misrouted into a false 503
 * (Q47 — a business answer is not a partial failure). The actual guard is in
 * {@link #getByIdFallback} itself, which inspects the throwable and rethrows
 * {@code FeignException.NotFound} unchanged instead of converting it.
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    Logger LOG = LoggerFactory.getLogger(UserServiceClient.class);

    @Retry(name = "userServiceLookup", fallbackMethod = "getByIdFallback")
    @CircuitBreaker(name = "userServiceLookup")
    @GetMapping("/api/v1/users/{id}")
    UserExistenceResponse getById(@PathVariable("id") UUID id);

    /**
     * Invoked once the Retry+CircuitBreaker chain above has given up on
     * {@link #getById} — <b>or</b> whenever {@link #getById} throws at all,
     * since Resilience4j-Spring's fallback wrapper catches unconditionally
     * and does not consult {@code ignore-exceptions} (see class Javadoc).
     * {@code FeignException.NotFound} must therefore be screened out here,
     * explicitly, rather than relied on to never arrive: it is rethrown
     * unchanged so it reaches {@code TransactionService}'s existing
     * {@code catch (FeignException.NotFound ex)} block exactly as it would
     * without any Resilience4j decoration at all.
     *
     * <p>Anything else reaching this point means user-service itself is the
     * problem, not the specific userId being looked up — either every retry
     * attempt failed, or the circuit was already OPEN and rejected the call
     * instantly (Q46 — "shielding your thread pool" by never even attempting
     * the call once tripped) — so it is a genuine, type-safe "can't tell you
     * right now" rather than a misclassified "doesn't exist" (Q47).
     */
    default UserExistenceResponse getByIdFallback(UUID id, Throwable throwable) {
        if (throwable instanceof FeignException.NotFound notFound) {
            throw notFound;
        }
        LOG.warn("user-service lookup fell back for userId={}: {}", id, throwable.toString());
        throw new DownstreamServiceUnavailableException("user-service", throwable);
    }
}
