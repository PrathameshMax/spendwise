package com.spendwise.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The caller, taken only from the validated access token — never from a URL,
 * query parameter or request body. {@code userId} is the token's {@code sub}
 * claim and is the owner id every query is scoped by
 * ({@code findByIdAndUserId(id, currentUser.userId())}).
 */
public record CurrentUser(UUID userId, String email, Set<String> roles) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SERVICE = "SERVICE";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    private static final String EMAIL_CLAIM = "email";

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isPrivileged() {
        return hasRole(ROLE_ADMIN) || hasRole(ROLE_SERVICE);
    }

    /**
     * Servlet services: the authenticated caller of the current request thread.
     */
    public static CurrentUser require() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static CurrentUser from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated bearer token on this request");
        }
        Jwt jwt = jwtAuthentication.getToken();
        UUID userId = parseSubject(jwt.getSubject());
        Set<String> roles = jwtAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(KeycloakGrantedAuthoritiesConverter.ROLE_PREFIX))
                .map(authority -> authority.substring(KeycloakGrantedAuthoritiesConverter.ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(userId, jwt.getClaimAsString(EMAIL_CLAIM), roles);
    }

    private static UUID parseSubject(String subject) {
        if (subject == null) {
            throw new BadCredentialsException("Access token has no sub claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Access token sub claim is not a UUID");
        }
    }
}
