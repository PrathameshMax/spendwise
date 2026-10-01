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
 */
public class DownstreamServiceUnavailableException extends SpendWiseException {

    private static final String ERROR_CODE = "DOWNSTREAM_SERVICE_UNAVAILABLE";

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE,
                "%s is temporarily unavailable — please retry shortly".formatted(serviceName), cause);
    }
}
