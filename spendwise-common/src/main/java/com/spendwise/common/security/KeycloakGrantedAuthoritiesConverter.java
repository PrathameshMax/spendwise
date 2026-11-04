package com.spendwise.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a Keycloak access token to Spring authorities.
 *
 * <ul>
 *   <li>{@code realm_access.roles} → {@code ROLE_<role>}, e.g. {@code ROLE_CUSTOMER},
 *       {@code ROLE_ADMIN}, {@code ROLE_SERVICE} — checked with {@code hasRole(...)}.</li>
 *   <li>{@code scope} → {@code SCOPE_<scope>}, e.g. {@code SCOPE_transactions:read} —
 *       checked with {@code hasAuthority(...)}.</li>
 * </ul>
 */
public class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String REALM_ACCESS_CLAIM = "realm_access";
    public static final String ROLES_KEY = "roles";
    public static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopeConverter.convert(jwt));
        Object realmAccess = jwt.getClaims().get(REALM_ACCESS_CLAIM);
        if (realmAccess instanceof Map<?, ?> realmAccessMap
                && realmAccessMap.get(ROLES_KEY) instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role instanceof String roleName && StringUtils.hasText(roleName)) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName));
                }
            }
        }
        return authorities;
    }
}
