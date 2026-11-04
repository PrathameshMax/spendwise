package com.spendwise.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a validated Keycloak JWT into the request's {@link JwtAuthenticationToken}:
 * principal name = {@code sub} (the Keycloak user id, also the owner id stored on
 * every SpendWise record), authorities from {@link KeycloakGrantedAuthoritiesConverter}.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakGrantedAuthoritiesConverter authoritiesConverter = new KeycloakGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt), jwt.getSubject());
    }
}
