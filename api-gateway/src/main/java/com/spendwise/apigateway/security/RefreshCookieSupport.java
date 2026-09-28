package com.spendwise.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spendwise.apigateway.config.GatewaySecurityProperties;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Implements the "rotate refresh tokens via secure cookies" half of the API
 * Gateway's filter pipeline (Milestone 6): auth-service only ever deals in a
 * {@code {accessToken, refreshToken, tokenType, expiresInSeconds}} JSON body —
 * it has no notion of cookies. These two rewrite functions sit in the
 * Gateway's request/response pipeline (wired in {@link com.spendwise.apigateway.config.RouteConfig})
 * to translate between that body shape and an HttpOnly, Secure,
 * SameSite=Strict cookie, so a refresh token never has to reach client-side
 * JavaScript.
 */
public final class RefreshCookieSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";

    private RefreshCookieSupport() {
    }

    /**
     * Request-side: the browser only carries the refresh token as an HttpOnly
     * cookie, so it cannot supply one in the request body itself. This rebuilds
     * the body auth-service's {@code RefreshRequest} expects, from that cookie.
     */
    public static Mono<String> injectRefreshTokenFromCookie(ServerWebExchange exchange,
                                                              GatewaySecurityProperties properties,
                                                              String originalBody) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(properties.getRefreshCookieName());
        String refreshToken = cookie == null ? "" : cookie.getValue();
        ObjectNode body = MAPPER.createObjectNode();
        body.put(FIELD_REFRESH_TOKEN, refreshToken);
        return Mono.just(body.toString());
    }

    /**
     * Response-side: auth-service's response body carries the refresh token in
     * plain JSON. This extracts it into a rotated Set-Cookie header and strips
     * it back out of the JSON body actually returned to the client.
     */
    public static Mono<String> rotateRefreshCookie(ServerWebExchange exchange,
                                                     GatewaySecurityProperties properties,
                                                     String originalBody) {
        try {
            JsonNode root = MAPPER.readTree(originalBody);
            if (root == null || !root.has(FIELD_REFRESH_TOKEN)) {
                return Mono.just(originalBody);
            }

            String refreshToken = root.get(FIELD_REFRESH_TOKEN).asText();
            ResponseCookie cookie = ResponseCookie.from(properties.getRefreshCookieName(), refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path(properties.getRefreshCookiePath())
                    .maxAge(properties.getRefreshCookieMaxAgeSeconds())
                    .build();
            exchange.getResponse().addCookie(cookie);

            ObjectNode mutable = ((ObjectNode) root).deepCopy();
            mutable.remove(FIELD_REFRESH_TOKEN);
            return Mono.just(mutable.toString());
        } catch (JsonProcessingException e) {
            return Mono.just(originalBody);
        }
    }
}
