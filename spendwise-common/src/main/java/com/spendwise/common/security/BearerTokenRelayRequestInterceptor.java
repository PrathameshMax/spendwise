package com.spendwise.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Hop ⑤ — no back doors: every Feign call between services carries a token the
 * callee validates like any other request.
 *
 * <ol>
 *   <li>A user is in context → relay the user's own access token, so the callee
 *       authorizes the real end user (ownership checks still apply downstream).</li>
 *   <li>No user (Kafka listener, scheduler, async thread without a propagated
 *       context) → this service's own client-credentials token
 *       ({@link ServiceTokenProvider}), which carries {@code ROLE_SERVICE}.</li>
 * </ol>
 */
public class BearerTokenRelayRequestInterceptor implements RequestInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<ServiceTokenProvider> serviceTokenProvider;

    public BearerTokenRelayRequestInterceptor(ObjectProvider<ServiceTokenProvider> serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken userToken) {
            template.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + userToken.getToken().getTokenValue());
            return;
        }
        ServiceTokenProvider provider = serviceTokenProvider.getIfAvailable();
        if (provider != null) {
            provider.serviceAccessToken()
                    .ifPresent(token -> template.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token));
        }
    }
}
