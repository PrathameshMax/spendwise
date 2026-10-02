package com.spendwise.apigateway.filter;

import com.spendwise.apigateway.config.GatewaySecurityProperties;
import com.spendwise.common.security.UserContextConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextPropagationGlobalFilterTest {

    private final AtomicInteger chainInvocations = new AtomicInteger();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain countingChain = exchange -> {
        chainInvocations.incrementAndGet();
        forwarded.set(exchange);
        return Mono.empty();
    };

    private UserContextPropagationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        GatewaySecurityProperties properties = mock(GatewaySecurityProperties.class);
        when(properties.getUserContextSecret()).thenReturn("test-user-context-secret-0123456789");
        filter = new UserContextPropagationGlobalFilter(properties);
    }

    @Test
    void anonymousRequestInvokesChainExactlyOnceAndStripsForgedHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/x").header(UserContextConstants.HEADER_NAME, "forged.value"));

        filter.filter(exchange, countingChain).block();

        assertThat(chainInvocations.get()).isEqualTo(1);
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(UserContextConstants.HEADER_NAME)).isFalse();
    }

    @Test
    void authenticatedRequestInvokesChainExactlyOnceWithSignedHeader() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("email", "smoke@spendwise.dev")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/x").header(UserContextConstants.HEADER_NAME, "forged.value"));

        filter.filter(exchange, countingChain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)))
                .block();

        assertThat(chainInvocations.get()).isEqualTo(1);
        String header = forwarded.get().getRequest().getHeaders().getFirst(UserContextConstants.HEADER_NAME);
        assertThat(header).isNotEqualTo("forged.value").matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    }

    @Test
    void unsignableSubjectFallsBackToSanitizedExchangeWithSingleInvocation() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("not-a-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/x"));

        filter.filter(exchange, countingChain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)))
                .block();

        assertThat(chainInvocations.get()).isEqualTo(1);
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(UserContextConstants.HEADER_NAME)).isFalse();
    }
}