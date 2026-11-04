package com.spendwise.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakGrantedAuthoritiesConverterTest {

    private static final String SUBJECT = UUID.randomUUID().toString();

    @Test
    void mapsRealmRolesToRolesAndScopesToScopeAuthorities() {
        Jwt jwt = keycloakToken(List.of("CUSTOMER", "ADMIN"), "openid transactions:read budgets:write");

        List<String> authorities = new KeycloakGrantedAuthoritiesConverter().convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "ROLE_CUSTOMER", "ROLE_ADMIN",
                "SCOPE_openid", "SCOPE_transactions:read", "SCOPE_budgets:write");
    }

    @Test
    void tokenWithoutRealmAccessGetsOnlyScopeAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim("scope", "users:read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_users:read");
    }

    @Test
    void currentUserIsBuiltFromSubjectEmailAndRolesOnly() {
        Jwt jwt = keycloakToken(List.of("CUSTOMER"), "transactions:read");
        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) new KeycloakJwtAuthenticationConverter().convert(jwt);

        CurrentUser currentUser = CurrentUser.from(authentication);

        assertThat(authentication.getName()).isEqualTo(SUBJECT);
        assertThat(currentUser.userId()).isEqualTo(UUID.fromString(SUBJECT));
        assertThat(currentUser.email()).isEqualTo("alice@spendwise.dev");
        assertThat(currentUser.roles()).containsExactly("CUSTOMER");
        assertThat(currentUser.isPrivileged()).isFalse();
    }

    private static Jwt keycloakToken(List<String> roles, String scope) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim("email", "alice@spendwise.dev")
                .claim("scope", scope)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
