package com.spendwise.common.security;

import java.util.Optional;

/**
 * Supplies this service's own access token (OAuth2 client credentials) for calls
 * made without a user in context — Kafka listeners, schedulers, background jobs.
 * Kept free of Spring Security OAuth2 client types so the Feign interceptor can
 * load in services that only relay user tokens.
 */
@FunctionalInterface
public interface ServiceTokenProvider {

    Optional<String> serviceAccessToken();
}
