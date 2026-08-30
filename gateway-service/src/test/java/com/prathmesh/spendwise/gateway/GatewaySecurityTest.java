package com.prathmesh.spendwise.gateway;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestSecurityConfig.class)
public class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedEndpoint_withoutJwt_shouldReturnUnauthorized() {

        webTestClient
                .get()
                .uri("/api/v1/does-not-exist")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void protectedEndpoint_withTestJwt_shouldNotBeUnauthorized() {

        webTestClient
                .get()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assertNotEquals(401, status.intValue());
                });
    }

    @Test
    void healthEndpoint_withoutJwt_shouldBeAllowed() {

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void infoEndpoint_withoutJwt_shouldBeAllowed() {

        webTestClient
                .get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
