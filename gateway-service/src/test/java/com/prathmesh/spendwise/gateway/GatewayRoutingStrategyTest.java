package com.prathmesh.spendwise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "USER_SERVICE_URL=http://localhost:8081",
        "TRANSACTION_SERVICE_URL=http://localhost:8082"
})
public class GatewayRoutingStrategyTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void userRoute_shouldOwnUserApiPaths() {

        Route userRoute = findRoute("user-service-route");

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .get("/api/v1/users/123")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        StepVerifier.create(
                        userRoute.getPredicate().apply(exchange)
                )
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void transactionRoute_shouldOwnTransactionApiPaths() {

        Route transactionRoute =
                findRoute("transaction-service-route");

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .get("/api/v1/transactions/123")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        StepVerifier.create(
                        transactionRoute.getPredicate().apply(exchange)
                )
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void userRoute_shouldNotOwnTransactionPaths() {

        Route userRoute = findRoute("user-service-route");

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .get("/api/v1/transactions/123")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        StepVerifier.create(
                        userRoute.getPredicate().apply(exchange)
                )
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void transactionRoute_shouldNotOwnUserPaths() {

        Route transactionRoute =
                findRoute("transaction-service-route");

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .get("/api/v1/users/123")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        StepVerifier.create(
                        transactionRoute.getPredicate().apply(exchange)
                )
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void userRoute_shouldUseConfiguredUserServiceUri() {

        Route userRoute = findRoute("user-service-route");

        assertEquals(
                "http://localhost:8081",
                userRoute.getUri().toString()
        );
    }

    @Test
    void transactionRoute_shouldUseConfiguredTransactionServiceUri() {

        Route transactionRoute =
                findRoute("transaction-service-route");

        assertEquals(
                "http://localhost:8082",
                transactionRoute.getUri().toString()
        );
    }

    @Test
    void unknownPath_shouldNotBelongToUserOrTransactionRoute() {

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .get("/api/v1/admin/test")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        Route userRoute = findRoute("user-service-route");
        Route transactionRoute = findRoute("transaction-service-route");

        StepVerifier.create(
                        userRoute.getPredicate().apply(exchange)
                )
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(
                        transactionRoute.getPredicate().apply(exchange)
                )
                .expectNext(false)
                .verifyComplete();
    }

    private Route findRoute(String routeId) {

        List<Route> routes = routeLocator
                .getRoutes()
                .collectList()
                .block();

        assertNotNull(routes);

        return routes.stream()
                .filter(route -> route.getId().equals(routeId))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Route not found: " + routeId
                        ));
    }
}
