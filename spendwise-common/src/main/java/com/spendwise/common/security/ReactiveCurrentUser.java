package com.spendwise.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * WebFlux twin of {@link CurrentUser#require()}: the security context lives in
 * the Reactor context, not a ThreadLocal, so it is read reactively. Kept in a
 * separate class so servlet services never load Reactor types.
 */
public final class ReactiveCurrentUser {

    private ReactiveCurrentUser() {
    }

    public static Mono<CurrentUser> require() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(CurrentUser::from)
                .switchIfEmpty(Mono.error(() ->
                        new AuthenticationCredentialsNotFoundException("No authenticated bearer token on this request")));
    }

    /**
     * The raw access token of the current request, for relaying it to a downstream
     * service (e.g. as gRPC {@code authorization} metadata).
     */
    public static Mono<String> bearerToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .ofType(JwtAuthenticationToken.class)
                .map(authentication -> authentication.getToken().getTokenValue());
    }
}
