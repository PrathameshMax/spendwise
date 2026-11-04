package com.spendwise.apigateway.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Backend-for-frontend session endpoint. The SPA never sees a token; it calls
 * {@code GET /bff/user} (cookie-authenticated) to learn who is logged in. A 401
 * tells it to navigate to {@code /oauth2/authorization/keycloak} to start login.
 */
@RestController
public class BffSessionController {

    @GetMapping("/bff/user")
    public Mono<BffUser> currentUser(Authentication authentication) {
        Map<String, Object> claims = claimsOf(authentication);
        return Mono.just(new BffUser(
                authentication.getName(),
                stringClaim(claims, "email"),
                stringClaim(claims, "name"),
                realmRoles(claims)));
    }

    private static Map<String, Object> claimsOf(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getClaims();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getTokenAttributes();
        }
        return Map.of();
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private static List<String> realmRoles(Map<String, Object> claims) {
        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess
                && realmAccess.get("roles") instanceof Collection<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    public record BffUser(String subject, String email, String name, List<String> roles) {
    }
}
