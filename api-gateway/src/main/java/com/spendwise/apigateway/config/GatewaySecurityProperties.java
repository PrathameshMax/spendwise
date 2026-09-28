package com.spendwise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code spendwise.security.*}. {@code jwkSetUri} and the refresh-cookie
 * settings are api-gateway-specific (config-repo/api-gateway.yml);
 * {@code userContextSecret} resolves to the same
 * {@code spendwise.security.user-context-secret} value defined once, globally,
 * in config-repo/application.yml — the Gateway signs the user-context header
 * with it, and every business service verifies with the identical value.
 */
@ConfigurationProperties(prefix = "spendwise.security")
public class GatewaySecurityProperties {

    private String jwkSetUri;
    private String userContextSecret;
    private String refreshCookieName = "spendwise_refresh_token";
    private String refreshCookiePath = "/api/v1/auth/refresh";
    private long refreshCookieMaxAgeSeconds = 604800;

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getUserContextSecret() {
        return userContextSecret;
    }

    public void setUserContextSecret(String userContextSecret) {
        this.userContextSecret = userContextSecret;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public String getRefreshCookiePath() {
        return refreshCookiePath;
    }

    public void setRefreshCookiePath(String refreshCookiePath) {
        this.refreshCookiePath = refreshCookiePath;
    }

    public long getRefreshCookieMaxAgeSeconds() {
        return refreshCookieMaxAgeSeconds;
    }

    public void setRefreshCookieMaxAgeSeconds(long refreshCookieMaxAgeSeconds) {
        this.refreshCookieMaxAgeSeconds = refreshCookieMaxAgeSeconds;
    }
}
