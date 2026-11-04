package com.spendwise.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.apigateway.security.AccessTokenClaimsValidator;
import com.spendwise.apigateway.security.AuthenticationProblemHandler;
import com.spendwise.apigateway.security.CsrfCookieWebFilter;
import com.spendwise.apigateway.security.GatewayJwtDecoder;
import com.spendwise.apigateway.security.JwksKeyCache;
import com.spendwise.common.security.KeycloakJwtAuthenticationConverter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Hop ② — one front door. The only public component; everything below it is
 * ClusterIP-only in Kubernetes.
 *
 * <pre>
 * Browser (SPA)                                   API client (mobile, Postman)
 *   HttpOnly SESSION cookie + X-XSRF-TOKEN          Authorization: Bearer &lt;jwt&gt;
 *        |                                                |
 *        v                                                v
 *   IdentityHeaderStrippingWebFilter (drop X-User-Id & co.)
 *   CORS allow-list -> security headers (HSTS, CSP, frame DENY, no-referrer)
 *   oauth2Login: Authorization Code + PKCE      oauth2ResourceServer: RS256 via JWKS,
 *   tokens kept in the Redis session            exp/iss/aud/typ/sub/email checks
 *        |                                                |
 *        +------------- authenticated --------------------+
 *   routes: rate limit per user (sub) / per IP, correlation id, request size cap,
 *           TokenRelay (session's access token -> Authorization header), Cookie removed
 *   failures: RFC 7807 401 / 503 (keys unreachable)
 * </pre>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] OAUTH2_PATHS = {"/oauth2/**", "/login/oauth2/**"};
    private static final String[] PUBLIC_ACTUATOR_PATHS = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus"};
    private static final String[] API_DOC_PATHS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         GatewaySecurityProperties properties,
                                                         ReactiveJwtDecoder jwtDecoder,
                                                         AuthenticationProblemHandler authenticationProblemHandler,
                                                         ServerOAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver,
                                                         ReactiveClientRegistrationRepository clientRegistrationRepository,
                                                         CorsConfigurationSource corsConfigurationSource) {
        String spaBaseUrl = properties.getSpaBaseUrl();
        boolean exposeApiDocs = properties.isExposeApiDocs();

        OidcClientInitiatedServerLogoutSuccessHandler keycloakLogout =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        keycloakLogout.setPostLogoutRedirectUri(spaBaseUrl + "/");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .headers(headers -> headers
                        .hsts(hsts -> hsts.maxAge(Duration.ofDays(365)).includeSubdomains(true).preload(true))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                        .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationProblemHandler))
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll();
                    exchanges.pathMatchers(OAUTH2_PATHS).permitAll();
                    exchanges.pathMatchers(PUBLIC_ACTUATOR_PATHS).permitAll();
                    exchanges.pathMatchers("/actuator/**").denyAll();
                    if (exposeApiDocs) {
                        exchanges.pathMatchers(API_DOC_PATHS).permitAll();
                    } else {
                        exchanges.pathMatchers(API_DOC_PATHS).denyAll();
                    }
                    exchanges.anyExchange().authenticated();
                })
                .oauth2Login(login -> login
                        .authorizationRequestResolver(pkceAuthorizationRequestResolver)
                        .loginPage("/oauth2/authorization/" + properties.getKeycloak().getRegistrationId())
                        .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler(spaBaseUrl + "/"))
                        .authenticationFailureHandler(new RedirectServerAuthenticationFailureHandler(spaBaseUrl + "/?login_error")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutHandler(new DelegatingServerLogoutHandler(
                                new SecurityContextServerLogoutHandler(), new WebSessionServerLogoutHandler()))
                        .logoutSuccessHandler(keycloakLogout))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationProblemHandler)
                        .authenticationFailureHandler(authenticationProblemHandler)
                        .jwt(jwtSpec -> jwtSpec
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        new ReactiveJwtAuthenticationConverterAdapter(new KeycloakJwtAuthenticationConverter()))))
                .addFilterAfter(new CsrfCookieWebFilter(), SecurityWebFiltersOrder.CSRF);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewaySecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN",
                "X-Correlation-ID", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("X-Correlation-ID", HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE));
        cors.setAllowCredentials(true);
        cors.setMaxAge(properties.getCors().getMaxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public JwksKeyCache jwksKeyCache(GatewaySecurityProperties properties,
                                     WebClient.Builder webClientBuilder,
                                     MeterRegistry meterRegistry) {
        GatewaySecurityProperties.JwksPolicy jwks = properties.getJwks();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(jwks.getConnectTimeout().toMillis()))
                .responseTimeout(jwks.getReadTimeout());
        WebClient webClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return new JwksKeyCache(webClient, properties.getKeycloak().internalEndpoint("certs"),
                jwks.getRefreshInterval(), jwks.getUnknownKidCooldown(), jwks.getFetchTimeout(),
                meterRegistry, Clock.systemUTC());
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwksKeyCache jwksKeyCache, GatewaySecurityProperties properties) {
        GatewaySecurityProperties.JwtPolicy policy = properties.getJwt();
        return GatewayJwtDecoder.create(jwksKeyCache, AccessTokenClaimsValidator.withStandardChecks(
                properties.getKeycloak().issuer(), policy.getAudience(), policy.getAcceptedTokenType(),
                policy.getClockSkew(), policy.getMaxTokenLifetime(), Clock.systemUTC()));
    }

    @Bean
    public AuthenticationProblemHandler authenticationProblemHandler(ObjectMapper objectMapper,
                                                                     MeterRegistry meterRegistry,
                                                                     GatewaySecurityProperties properties) {
        return new AuthenticationProblemHandler(objectMapper, meterRegistry, properties.getJwt().getRealm(),
                properties.getJwks().getUnavailableRetryAfter(), Clock.systemUTC());
    }
}
