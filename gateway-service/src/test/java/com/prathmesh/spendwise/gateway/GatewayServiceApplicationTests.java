package com.prathmesh.spendwise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.context.annotation.Import;


@Import(GatewayTestSecurityConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GatewayServiceApplicationTests {
    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Test
    void gatewayApplication_shouldStartSuccessfully() {
        assertTrue(port > 0);
    }

    @Test
    void healthEndpoint_shouldReturnUp() {

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void infoEndpoint_shouldBeAvailable() {

        webTestClient
                .get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.name")
                .isEqualTo("SpendWise Gateway");
    }

    @Test
    void metricsEndpoint_shouldBeAvailable() {

        webTestClient
                .get()
                .uri("/actuator/metrics")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.names")
                .isArray();
    }

    @Test
    void gatewayApplication_shouldLoadUserServiceRoute() {

        webTestClient
                .get()
                .uri("/actuator/gateway/routes")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.route_id == 'user-service-route')]")
                .exists();
    }
}
