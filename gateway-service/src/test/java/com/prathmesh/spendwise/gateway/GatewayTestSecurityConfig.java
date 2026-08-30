package com.prathmesh.spendwise.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;

@TestConfiguration
public class GatewayTestSecurityConfig {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {

        return token -> {

            System.out.println("========== TEST JWT DECODER CALLED ==========");
            System.out.println("TOKEN = " + token);
            System.out.println("=============================================");

            if (!"test-token".equals(token)) {
                return Mono.error(new JwtException("Invalid test token"));
            }

            Instant now = Instant.now();

            Jwt jwt = Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .claim("scope", "read")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();

            System.out.println("========== TEST JWT CREATED ================");
            System.out.println(jwt);
            System.out.println("=============================================");

            return Mono.just(jwt);
        };
    }
}
