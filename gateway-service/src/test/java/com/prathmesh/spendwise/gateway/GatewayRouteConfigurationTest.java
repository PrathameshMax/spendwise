package com.prathmesh.spendwise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;

import java.net.URI;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest
public class GatewayRouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void userServiceRoute_shouldBeConfigured() {

        List<Route> routes = routeLocator
                .getRoutes()
                .collectList()
                .block();

        assertNotNull(routes);

        Route userRoute = routes.stream()
                .filter(route -> route.getId().equals("user-service-route"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                "http://localhost:8081",
                userRoute.getUri().toString()
        );
    }

    @Test
    void userServiceRoute_shouldMatchUsersPath() {

        List<Route> routes = routeLocator
                .getRoutes()
                .collectList()
                .block();

        Route userRoute = routes.stream()
                .filter(route -> route.getId().equals("user-service-route"))
                .findFirst()
                .orElseThrow();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/1")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        assertTrue(
                Mono.from(
                        userRoute.getPredicate().apply(exchange)
                ).block()
        );
    }
}
