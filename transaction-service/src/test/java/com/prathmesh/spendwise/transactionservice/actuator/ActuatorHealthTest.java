package com.prathmesh.spendwise.transactionservice.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ActuatorHealthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointShouldReturnUp() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "http://localhost:" + port + "/actuator/health",
                        Map.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("status", Status.UP.getCode());
    }

    @Test
    void livenessEndpointShouldReturnUp() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "http://localhost:" + port + "/actuator/health/liveness",
                        Map.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("status", Status.UP.getCode());
    }

    @Test
    void readinessEndpointShouldReturnUp() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "http://localhost:" + port + "/actuator/health/readiness",
                        Map.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("status", Status.UP.getCode());
    }

    @Test
    void metricsEndpointShouldBeAvailable() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "http://localhost:" + port + "/actuator/metrics",
                        Map.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("names");
    }

    @Test
    void httpServerRequestsMetricShouldBeAvailable() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "http://localhost:" + port
                                + "/actuator/metrics/http.server.requests",
                        Map.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("name", "http.server.requests");
    }
}
