package com.spendwise.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.Map;

/**
 * Hop ① — the gateway as the OIDC client of Keycloak (backend-for-frontend).
 *
 * <ul>
 *   <li>Authorization Code flow with PKCE (S256), even though the client is
 *       confidential: PKCE binds the code to this login attempt, so an
 *       intercepted code is useless.</li>
 *   <li>Browser-facing endpoints ({@code authorize}, {@code logout}) use the public
 *       Keycloak URL; back-channel endpoints ({@code token}, {@code certs},
 *       {@code userinfo}) use the in-network URL. The registration is declared in
 *       code, not via {@code issuer-uri} discovery, because the public issuer is
 *       not reachable from inside the container network.</li>
 *   <li>Access, refresh and ID tokens are stored in the server-side WebSession
 *       (Redis), never sent to the browser. The authorized-client manager refreshes
 *       the access token with the refresh token when it expires; Keycloak rotates
 *       the refresh token on every use.</li>
 * </ul>
 */
@Configuration
public class KeycloakClientConfig {

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.Keycloak keycloak = properties.getKeycloak();
        ClientRegistration registration = ClientRegistration.withRegistrationId(keycloak.getRegistrationId())
                .clientName("Keycloak")
                .clientId(keycloak.getClientId())
                .clientSecret(keycloak.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(keycloak.getScopes())
                .issuerUri(keycloak.issuer())
                .authorizationUri(keycloak.publicEndpoint("auth"))
                .tokenUri(keycloak.internalEndpoint("token"))
                .jwkSetUri(keycloak.internalEndpoint("certs"))
                .userInfoUri(keycloak.internalEndpoint("userinfo"))
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", keycloak.publicEndpoint("logout")))
                .build();
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }

    @Bean
    public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        DefaultReactiveOAuth2AuthorizedClientManager manager =
                new DefaultReactiveOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    @Bean
    public ServerOAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        DefaultServerOAuth2AuthorizationRequestResolver resolver =
                new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}
