package com.spendwise.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * WebFlux twin of {@link SpendwiseResourceServerAutoConfiguration} (analytics-service).
 * Backs off entirely when the application defines its own
 * {@link SecurityWebFilterChain} — api-gateway does, as the BFF.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = {
        "org.springframework.security.config.web.server.ServerHttpSecurity",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
        "org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder"
})
@ConditionalOnProperty(prefix = "spendwise.security.resource-server", name = "issuer")
@ConditionalOnMissingBean(type = "org.springframework.security.web.server.SecurityWebFilterChain")
@EnableConfigurationProperties(SpendwiseResourceServerProperties.class)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SpendwiseReactiveResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder reactiveJwtDecoder(SpendwiseResourceServerProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(SpendwiseJwtValidators.accessToken(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveSecurityProblemHandler reactiveSecurityProblemHandler(ObjectMapper objectMapper) {
        return new ReactiveSecurityProblemHandler(objectMapper);
    }

    @Bean
    public SecurityWebFilterChain spendwiseReactiveResourceServerFilterChain(ServerHttpSecurity http,
                                                                            ReactiveJwtDecoder jwtDecoder,
                                                                            ReactiveSecurityProblemHandler problemHandler,
                                                                            SpendwiseResourceServerProperties properties) {
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers(SpendwiseResourceServerAutoConfiguration.PUBLIC_ACTUATOR_PATHS).permitAll();
                    exchanges.pathMatchers(SpendwiseResourceServerAutoConfiguration.ACTUATOR_PATHS)
                            .hasRole(CurrentUser.ROLE_ADMIN);
                    if (publicPaths.length > 0) {
                        exchanges.pathMatchers(publicPaths).permitAll();
                    }
                    exchanges.anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        new ReactiveJwtAuthenticationConverterAdapter(new KeycloakJwtAuthenticationConverter())))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }
}
