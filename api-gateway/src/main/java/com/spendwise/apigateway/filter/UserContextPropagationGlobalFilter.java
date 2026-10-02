package com.spendwise.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.apigateway.config.GatewaySecurityProperties;
import com.spendwise.common.security.UserContext;
import com.spendwise.common.security.UserContextConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

/**
 * The Gateway's half of the "forward a sanitized, signed user-context header
 * downstream" requirement (Milestone 6). Runs after the resource-server filter
 * has validated the JWT, so a verified {@link Jwt} is available in the reactive
 * security context whenever the route required authentication.
 *
 * Always strips any inbound {@value UserContextConstants#HEADER_NAME} header
 * first ("sanitized") so a client cannot forge it; business services verify the
 * HMAC signature with a secret only the Gateway and the services hold.
 *
 * The exchange to forward is resolved first and {@code chain.filter} is invoked
 * exactly once. {@code chain.filter} returns an always-empty {@code Mono<Void>},
 * so placing it inside {@code flatMap} followed by
 * {@code switchIfEmpty(chain.filter(...))} re-executes the whole downstream chain
 * after the response is committed: the rate limiter then writes headers onto a
 * read-only response (UnsupportedOperationException), 429s degrade to empty 500s,
 * 200s are truncated, every request consumes two rate-limit tokens, and any route
 * without a limiter would be proxied to the business service twice.
 */
@Component
public class UserContextPropagationGlobalFilter implements GlobalFilter, Ordered {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewaySecurityProperties securityProperties;

    public UserContextPropagationGlobalFilter(GatewaySecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(UserContextConstants.HEADER_NAME))
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .ofType(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> withUserContext(sanitizedExchange, jwt))
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withUserContext(ServerWebExchange exchange, Jwt jwt) {
        try {
            UserContext context = new UserContext(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
            ServerHttpRequest requestWithContext = exchange.getRequest().mutate()
                    .header(UserContextConstants.HEADER_NAME, sign(context))
                    .build();
            return exchange.mutate().request(requestWithContext).build();
        } catch (GeneralSecurityException | IllegalArgumentException | JsonProcessingException e) {
            return exchange;
        }
    }

    private String sign(UserContext context) throws GeneralSecurityException, JsonProcessingException {
        byte[] payloadBytes = MAPPER.writeValueAsBytes(context);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(securityProperties.getUserContextSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal(payloadBytes);

        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return encodedPayload + "." + encodedSignature;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}