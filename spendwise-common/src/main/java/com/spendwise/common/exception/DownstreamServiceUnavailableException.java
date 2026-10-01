package com.spendwise.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Milestone 12 — the type-safe fallback outcome for a Resilience4j-protected
 * downstream call that could not be completed: the circuit is open, or the
 * call itself failed for a reason judged genuinely transient/infrastructural
 * rather than a legitimate business answer (a 404 is never routed here — see
 * {@code UserServiceClient.getByIdFallback} and the
 * {@code resilience4j.circuitbreaker}/{@code resilience4j.retry}
 * {@code ignore-exceptions} entries that keep it out).
 *
 * <p>Deliberately generic — parameterized by {@code serviceName} rather than
 * named after any one downstream — so any future circuit-breaker-protected
 * call (any service, not just transaction-service's call to user-service) can
 * reuse this same exception instead of each inventing its own, consistent
 * with this module's "zero business logic, only generic plumbing" rule (see
 * README, "Path to Polyrepo").
 *
 * <p>Note for any future caller wiring up a {@code fallbackMethod} of its
 * own: Resilience4j's {@code ignore-exceptions} config does not prevent a
 * fallback from being invoked for an ignored exception — it only controls
 * the Retry/CircuitBreaker core decision (whether to retry, whether it
 * counts toward the failure-rate window). A fallback that should pass a
 * particular exception through untouched (a 404, say) must check for it
 * explicitly and rethrow, as {@code UserServiceClient.getByIdFallback}
 * does, rather than relying on {@code ignore-exceptions} alone.
 */
public class DownstreamServiceUnavailableException extends SpendWiseException {

    private static final String ERROR_CODE = "DOWNSTREAM_SERVICE_UNAVAILABLE";

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE,
                "%s is temporarily unavailable — please retry shortly".formatted(serviceName), cause);
    }
}
