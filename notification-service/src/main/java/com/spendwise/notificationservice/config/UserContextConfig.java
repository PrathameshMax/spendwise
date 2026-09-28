package com.spendwise.notificationservice.config;

import com.spendwise.common.security.UserContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link UserContextFilter} from spendwise-common so this
 * service can trust the caller identity the API Gateway resolves from the
 * validated JWT and forwards as a signed header (Milestone 6), without
 * decoding/validating the JWT itself — only the Gateway holds the JWKS-based
 * verification logic.
 */
@Configuration
public class UserContextConfig {

    @Value("${spendwise.security.user-context-secret}")
    private String userContextSecret;

    @Bean
    public FilterRegistrationBean<UserContextFilter> userContextFilter() {
        FilterRegistrationBean<UserContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserContextFilter(userContextSecret));
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        registration.setName("userContextFilter");
        return registration;
    }
}
