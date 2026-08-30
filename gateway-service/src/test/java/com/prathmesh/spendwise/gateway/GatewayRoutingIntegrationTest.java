package com.prathmesh.spendwise.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import org.springframework.context.annotation.Import;

@Import(GatewayTestSecurityConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "USER_SERVICE_URL=http://localhost:8081",
                "TRANSACTION_SERVICE_URL=http://localhost:8082"
        }
)
public class GatewayRoutingIntegrationTest {

    private static final WireMockServer userService =
            new WireMockServer(0);

    private static final WireMockServer transactionService =
            new WireMockServer(0);

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int gatewayPort;

    @BeforeAll
    static void startMockServers() {

        userService.start();
        transactionService.start();
    }

    @AfterAll
    static void stopMockServers() {

        userService.stop();
        transactionService.stop();
    }

    @BeforeEach
    void resetMocks() {

        userService.resetAll();
        transactionService.resetAll();
    }

    @DynamicPropertySource
    static void registerProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "USER_SERVICE_URL",
                () -> userService.baseUrl()
        );

        registry.add(
                "TRANSACTION_SERVICE_URL",
                () -> transactionService.baseUrl()
        );
    }

    @Test
    void userRoute_shouldForwardGetRequest() {

        userService.stubFor(
                get(urlEqualTo("/api/v1/users/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody("user-service")
                        )
        );

        webTestClient
                .get()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("user-service");

        userService.verify(
                1,
                getRequestedFor(
                        urlEqualTo("/api/v1/users/123")
                )
        );

        transactionService.verify(
                0,
                anyRequestedFor(anyUrl())
        );
    }

    @Test
    void transactionRoute_shouldForwardGetRequest() {

        transactionService.stubFor(
                get(urlEqualTo("/api/v1/transactions/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody("transaction-service")
                        )
        );

        webTestClient
                .get()
                .uri("/api/v1/transactions/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("transaction-service");

        transactionService.verify(
                1,
                getRequestedFor(
                        urlEqualTo("/api/v1/transactions/123")
                )
        );

        userService.verify(
                0,
                anyRequestedFor(anyUrl())
        );
    }

    @Test
    void userRoute_shouldPreserveQueryParameters() {

        userService.stubFor(
                get(urlEqualTo("/api/v1/users?page=0&size=10"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        webTestClient
                .get()
                .uri("/api/v1/users?page=0&size=10")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

        userService.verify(
                1,
                getRequestedFor(
                        urlEqualTo(
                                "/api/v1/users?page=0&size=10"
                        )
                )
        );
    }

    @Test
    void userRoute_shouldForwardPostRequestBody() {

        String requestBody = """
                {
                    "name": "Gateway Test User"
                }
                """;

        userService.stubFor(
                post(urlEqualTo("/api/v1/users"))
                        .withRequestBody(
                                equalToJson(requestBody)
                        )
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                        )
        );

        webTestClient
                .post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer test-token")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated();

        userService.verify(
                1,
                postRequestedFor(
                        urlEqualTo("/api/v1/users")
                ).withRequestBody(
                        equalToJson(requestBody)
                )
        );
    }

    @Test
    void userRoute_shouldForwardPutRequest() {

        String requestBody = """
                {
                    "name": "Updated User"
                }
                """;

        userService.stubFor(
                put(urlEqualTo("/api/v1/users/123"))
                        .withRequestBody(
                                equalToJson(requestBody)
                        )
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        webTestClient
                .put()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer test-token")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        userService.verify(
                1,
                putRequestedFor(
                        urlEqualTo("/api/v1/users/123")
                ).withRequestBody(
                        equalToJson(requestBody)
                )
        );
    }

    @Test
    void userRoute_shouldForwardDeleteRequest() {

        userService.stubFor(
                delete(urlEqualTo("/api/v1/users/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(204)
                        )
        );

        webTestClient
                .delete()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNoContent();

        userService.verify(
                1,
                deleteRequestedFor(
                        urlEqualTo("/api/v1/users/123")
                )
        );
    }

    @Test
    void transactionRoute_shouldForwardPostRequest() {

        String requestBody = """
                {
                    "amount": 500
                }
                """;

        transactionService.stubFor(
                post(urlEqualTo("/api/v1/transactions"))
                        .withRequestBody(
                                equalToJson(requestBody)
                        )
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                        )
        );

        webTestClient
                .post()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer test-token")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated();

        transactionService.verify(
                1,
                postRequestedFor(
                        urlEqualTo("/api/v1/transactions")
                ).withRequestBody(
                        equalToJson(requestBody)
                )
        );
    }

    @Test
    void transactionRoute_shouldForwardDeleteRequest() {

        transactionService.stubFor(
                delete(urlEqualTo("/api/v1/transactions/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(204)
                        )
        );

        webTestClient
                .delete()
                .uri("/api/v1/transactions/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNoContent();

        transactionService.verify(
                1,
                deleteRequestedFor(
                        urlEqualTo("/api/v1/transactions/123")
                )
        );
    }

    @Test
    void unknownPath_shouldNotReachUserService() {

        webTestClient
                .get()
                .uri("/api/v1/unknown")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().is4xxClientError();

        userService.verify(
                0,
                anyRequestedFor(anyUrl())
        );

        transactionService.verify(
                0,
                anyRequestedFor(anyUrl())
        );
    }

    @Test
    void userPath_shouldNeverReachTransactionService() {

        userService.stubFor(
                get(urlEqualTo("/api/v1/users/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        webTestClient
                .get()
                .uri("/api/v1/users/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

        transactionService.verify(
                0,
                anyRequestedFor(anyUrl())
        );
    }

    @Test
    void transactionPath_shouldNeverReachUserService() {

        transactionService.stubFor(
                get(urlEqualTo("/api/v1/transactions/123"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        webTestClient
                .get()
                .uri("/api/v1/transactions/123")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

        userService.verify(
                0,
                anyRequestedFor(anyUrl())
        );
    }

    private WebTestClient.RequestHeadersSpec<?> authenticated(
            WebTestClient.RequestHeadersSpec<?> request) {

        return request.header(
                "Authorization",
                "Bearer test-token"
        );
    }
}
