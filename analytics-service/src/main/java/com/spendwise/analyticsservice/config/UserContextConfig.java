package com.spendwise.analyticsservice.config;

import com.spendwise.common.security.ReactiveUserContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link ReactiveUserContextFilter} from spendwise-common so
 * this service can trust the caller identity the API Gateway resolves from the
 * validated JWT and forwards as a signed header (Milestone 6), without decoding/
 * validating the JWT itself. Milestone 15 rebuild of this class: see
 * {@link TracingConfig}'s Javadoc for why a plain {@code @Bean} of the
 * {@code WebFilter} type is now all that is needed, replacing the servlet
 * stack's {@code FilterRegistrationBean} this class previously returned.
 */
@Configuration
public class UserContextConfig {

    @Value("${spendwise.security.user-context-secret}")
    private String userContextSecret;

    @Bean
    public ReactiveUserContextFilter userContextFilter() {
        return new ReactiveUserContextFilter(userContextSecret);
    }
}
