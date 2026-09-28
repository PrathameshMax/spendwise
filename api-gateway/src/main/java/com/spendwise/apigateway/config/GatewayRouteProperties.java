package com.spendwise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds {@code spendwise.gateway.routes} (config-repo/api-gateway.yml) — the
 * base URI of every business service the Gateway proxies to. No discovery
 * server exists yet (Milestone 10), so these are static host:port values,
 * centrally configured, exactly like every other service's datasource URL.
 */
@ConfigurationProperties(prefix = "spendwise.gateway")
public class GatewayRouteProperties {

    private Map<String, String> routes = Map.of();

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    public String uriFor(String serviceName) {
        String uri = routes.get(serviceName);
        if (uri == null) {
            throw new IllegalStateException("No route URI configured for service '" + serviceName + "'");
        }
        return uri;
    }
}
