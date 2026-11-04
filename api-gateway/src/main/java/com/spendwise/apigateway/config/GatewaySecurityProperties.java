package com.spendwise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code spendwise.security.*} for the gateway (config-repo/api-gateway.yml).
 *
 * <ul>
 *   <li>{@code keycloak} — realm URLs: {@code public-url} is what the browser and
 *       the token's {@code iss} use; {@code internal-url} is the in-network address
 *       for token exchange and JWKS (they differ inside Docker/Kubernetes).</li>
 *   <li>{@code spa-base-url} — where the browser lands after login/logout.</li>
 *   <li>{@code cors} — the explicit allow-list of browser origins.</li>
 *   <li>{@code jwt} / {@code jwks} — bearer-token acceptance policy and key caching.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "spendwise.security")
public class GatewaySecurityProperties {

    private final Keycloak keycloak = new Keycloak();
    private final Cors cors = new Cors();
    private final JwtPolicy jwt = new JwtPolicy();
    private final JwksPolicy jwks = new JwksPolicy();
    private String spaBaseUrl = "http://localhost:5173";
    private boolean exposeApiDocs = false;
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public Cors getCors() {
        return cors;
    }

    public JwtPolicy getJwt() {
        return jwt;
    }

    public JwksPolicy getJwks() {
        return jwks;
    }

    public String getSpaBaseUrl() {
        return spaBaseUrl;
    }

    public void setSpaBaseUrl(String spaBaseUrl) {
        this.spaBaseUrl = spaBaseUrl;
    }

    public boolean isExposeApiDocs() {
        return exposeApiDocs;
    }

    public void setExposeApiDocs(boolean exposeApiDocs) {
        this.exposeApiDocs = exposeApiDocs;
    }

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public static class Keycloak {

        private String publicUrl = "http://localhost:8180";
        private String internalUrl = "http://localhost:8180";
        private String realm = "spendwise";
        private String registrationId = "keycloak";
        private String clientId = "spendwise-bff";
        private String clientSecret;
        private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));

        public String issuer() {
            return publicUrl + "/realms/" + realm;
        }

        public String publicEndpoint(String path) {
            return issuer() + "/protocol/openid-connect/" + path;
        }

        public String internalEndpoint(String path) {
            return internalUrl + "/realms/" + realm + "/protocol/openid-connect/" + path;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getInternalUrl() {
            return internalUrl;
        }

        public void setInternalUrl(String internalUrl) {
            this.internalUrl = internalUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        private Duration maxAge = Duration.ofHours(1);

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    public static class JwtPolicy {

        private String audience = "spendwise-api";
        private String acceptedTokenType = "Bearer";
        private Duration clockSkew = Duration.ofSeconds(30);
        private Duration maxTokenLifetime = Duration.ofHours(1);
        private String realm = "spendwise";

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getAcceptedTokenType() {
            return acceptedTokenType;
        }

        public void setAcceptedTokenType(String acceptedTokenType) {
            this.acceptedTokenType = acceptedTokenType;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public Duration getMaxTokenLifetime() {
            return maxTokenLifetime;
        }

        public void setMaxTokenLifetime(Duration maxTokenLifetime) {
            this.maxTokenLifetime = maxTokenLifetime;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }
    }

    public static class JwksPolicy {

        private Duration refreshInterval = Duration.ofMinutes(5);
        private Duration unknownKidCooldown = Duration.ofSeconds(15);
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(2);
        private Duration fetchTimeout = Duration.ofSeconds(3);
        private Duration unavailableRetryAfter = Duration.ofSeconds(5);

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public Duration getUnknownKidCooldown() {
            return unknownKidCooldown;
        }

        public void setUnknownKidCooldown(Duration unknownKidCooldown) {
            this.unknownKidCooldown = unknownKidCooldown;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getFetchTimeout() {
            return fetchTimeout;
        }

        public void setFetchTimeout(Duration fetchTimeout) {
            this.fetchTimeout = fetchTimeout;
        }

        public Duration getUnavailableRetryAfter() {
            return unavailableRetryAfter;
        }

        public void setUnavailableRetryAfter(Duration unavailableRetryAfter) {
            this.unavailableRetryAfter = unavailableRetryAfter;
        }
    }
}
