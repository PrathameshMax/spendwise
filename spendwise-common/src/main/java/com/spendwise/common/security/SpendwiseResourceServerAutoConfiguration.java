package com.spendwise.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hop ③ — every servlet microservice is an OAuth2 Resource Server and validates
 * the JWT again (zero trust), instead of trusting the gateway.
 *
 * <pre>
 * Authorization: Bearer &lt;jwt&gt;
 *   -> BearerTokenAuthenticationFilter
 *   -> NimbusJwtDecoder: Keycloak JWKS (kid lookup) + RS256 signature
 *   -> SpendwiseJwtValidators: exp/nbf, iss, aud, typ=Bearer
 *   -> KeycloakJwtAuthenticationConverter: realm roles -> ROLE_*, scope -> SCOPE_*
 *   -> URL rules here, then @PreAuthorize on controller methods
 *   no/invalid token -> 401, valid token without permission -> 403 (RFC 7807)
 * </pre>
 *
 * Active when {@code spendwise.security.resource-server.issuer} is set and the
 * service has spring-boot-starter-oauth2-resource-server; a service that declares
 * its own {@link SecurityFilterChain} replaces the chain below. Ordered before
 * Boot's security auto-configurations so their defaults back off.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
        "org.springframework.security.oauth2.jwt.NimbusJwtDecoder"
})
@ConditionalOnProperty(prefix = "spendwise.security.resource-server", name = "issuer")
@EnableConfigurationProperties(SpendwiseResourceServerProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class SpendwiseResourceServerAutoConfiguration {

    static final String[] PUBLIC_ACTUATOR_PATHS = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus"};
    static final String ACTUATOR_PATHS = "/actuator/**";

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(SpendwiseResourceServerProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(SpendwiseJwtValidators.accessToken(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public ServletSecurityProblemHandler servletSecurityProblemHandler(ObjectMapper objectMapper) {
        return new ServletSecurityProblemHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain spendwiseResourceServerFilterChain(HttpSecurity http,
                                                                  JwtDecoder jwtDecoder,
                                                                  ServletSecurityProblemHandler problemHandler,
                                                                  SpendwiseResourceServerProperties properties)
            throws Exception {
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(PUBLIC_ACTUATOR_PATHS).permitAll();
                    requests.requestMatchers(ACTUATOR_PATHS).hasRole(CurrentUser.ROLE_ADMIN);
                    if (publicPaths.length > 0) {
                        requests.requestMatchers(publicPaths).permitAll();
                    }
                    requests.requestMatchers("/error").permitAll();
                    requests.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }
}
