package com.spendwise.common.security;

import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Optional;

/**
 * Service-to-service tokens for Feign clients: user-token relay always, plus a
 * client-credentials {@link ServiceTokenProvider} when the service declares a
 * {@code spring.security.oauth2.client.registration.spendwise-service} registration.
 *
 * <p>Uses {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} (not the
 * request-scoped default manager) because background threads have no HTTP
 * request; the token is cached and re-fetched shortly before it expires.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "feign.RequestInterceptor",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken"
})
public class SpendwiseServiceTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "bearerTokenRelayRequestInterceptor")
    public RequestInterceptor bearerTokenRelayRequestInterceptor(ObjectProvider<ServiceTokenProvider> serviceTokenProvider) {
        return new BearerTokenRelayRequestInterceptor(serviceTokenProvider);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager")
    @ConditionalOnBean(type = "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository")
    static class ClientCredentialsConfiguration {

        private static final Logger log = LoggerFactory.getLogger(ClientCredentialsConfiguration.class);

        @Bean
        @ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
        OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
                ClientRegistrationRepository clientRegistrationRepository,
                ObjectProvider<OAuth2AuthorizedClientService> authorizedClientService) {
            OAuth2AuthorizedClientService clientService = authorizedClientService.getIfAvailable(
                    () -> new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository));
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                    new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
            manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build());
            return manager;
        }

        @Bean
        @ConditionalOnMissingBean
        ServiceTokenProvider clientCredentialsServiceTokenProvider(
                OAuth2AuthorizedClientManager serviceAuthorizedClientManager,
                @Value("${spendwise.security.service-client.registration-id:spendwise-service}") String registrationId,
                @Value("${spring.application.name:spendwise-service}") String applicationName) {
            return () -> {
                try {
                    OAuth2AuthorizedClient client = serviceAuthorizedClientManager.authorize(
                            OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                                    .principal(applicationName)
                                    .build());
                    return Optional.ofNullable(client).map(c -> c.getAccessToken().getTokenValue());
                } catch (RuntimeException e) {
                    log.warn("Could not obtain client-credentials token for registration={}: {}",
                            registrationId, e.toString());
                    return Optional.empty();
                }
            };
        }
    }
}
