package com.prathmesh.spendwise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = {
                "USER_SERVICE_URL=http://localhost:8081",
                "TRANSACTION_SERVICE_URL=http://localhost:8082"
        }
)
public class GatewayRouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Value("${USER_SERVICE_URL}")
    private String userServiceUrl;

    @Value("${TRANSACTION_SERVICE_URL}")
    private String transactionServiceUrl;

    @Test
    void userServiceRoute_shouldBeConfigured() {

        Route userRoute = getRoute("user-service-route");

        assertEquals(
                userServiceUrl,
                userRoute.getUri().toString()
        );
    }

    @Test
    void transactionServiceRoute_shouldBeConfigured() {

        Route transactionRoute = getRoute("transaction-service-route");

        assertEquals(
                transactionServiceUrl,
                transactionRoute.getUri().toString()
        );
    }

    @Test
    void userServiceRoute_shouldMatchUsersPath() {

        Route userRoute = getRoute("user-service-route");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/1")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertTrue(
                Mono.from(userRoute.getPredicate().apply(exchange))
                        .block()
        );
    }

    @Test
    void transactionServiceRoute_shouldMatchTransactionsPath() {

        Route transactionRoute = getRoute("transaction-service-route");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/transactions/1")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertTrue(
                Mono.from(transactionRoute.getPredicate().apply(exchange))
                        .block()
        );
    }

    @Test
    void userServiceRoute_shouldNotMatchTransactionPath() {

        Route userRoute = getRoute("user-service-route");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/transactions/1")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertFalse(
                Mono.from(userRoute.getPredicate().apply(exchange))
                        .block()
        );
    }

    @Test
    void transactionServiceRoute_shouldNotMatchUserPath() {

        Route transactionRoute = getRoute("transaction-service-route");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/1")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertNotEquals(Boolean.TRUE, Mono.from(transactionRoute.getPredicate().apply(exchange))
                .block());
    }

    @Test
    void userServiceRoute_shouldNotMatchUnknownPath() {

        Route userRoute = getRoute("user-service-route");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/unknown")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertNotEquals(Boolean.TRUE, Mono.from(userRoute.getPredicate().apply(exchange))
                .block());
    }

    private Route getRoute(String routeId) {

        List<Route> routes = routeLocator
                .getRoutes()
                .collectList()
                .block();

        assertNotNull(routes);

        return routes.stream()
                .filter(route -> route.getId().equals(routeId))
                .findFirst()
                .orElseThrow();
    }
}
