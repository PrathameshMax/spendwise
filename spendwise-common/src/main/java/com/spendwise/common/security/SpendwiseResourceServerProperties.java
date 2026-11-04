package com.spendwise.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code spendwise.security.resource-server.*} — how every microservice validates
 * the Keycloak access token on its own (zero trust: a request is not trusted just
 * because the gateway already checked it).
 *
 * <ul>
 *   <li>{@code issuer} — the browser-facing Keycloak realm URL; must equal the
 *       token's {@code iss} exactly.</li>
 *   <li>{@code jwkSetUri} — the in-network certs endpoint the public keys are
 *       fetched from (differs from {@code issuer} inside Docker/Kubernetes).</li>
 *   <li>{@code audience} — the API audience Keycloak's audience mapper adds;
 *       a token minted for another API is rejected.</li>
 *   <li>{@code publicPaths} — extra unauthenticated paths (API docs in dev only).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "spendwise.security.resource-server")
public class SpendwiseResourceServerProperties {

    private String issuer;
    private String jwkSetUri;
    private String audience = "spendwise-api";
    private String acceptedTokenType = "Bearer";
    private Duration clockSkew = Duration.ofSeconds(30);
    private List<String> publicPaths = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

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

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
