package com.spendwise.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Makes the Gateway an OAuth2 Resource Server (Milestone 6): every request
 * other than register/login/refresh and actuator must carry a JWT that
 * verifies against auth-service's {@code /oauth2/jwks} public key. Business
 * services behind the Gateway no longer need this configuration themselves —
 * they trust the signed {@code X-User-Context} header the Gateway forwards
 * instead (see {@code UserContextPropagationGlobalFilter}), rather than each
 * re-fetching the JWKS and re-validating the JWT independently.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)));
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewaySecurityProperties properties) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
    }
}
