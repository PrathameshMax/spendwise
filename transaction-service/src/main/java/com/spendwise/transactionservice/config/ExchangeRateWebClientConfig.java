package com.spendwise.transactionservice.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Milestone 13 — same defensive-timeout reasoning Milestone 11 applied to
 * every OpenFeign client ({@code config-repo/application.yml}'s
 * {@code spring.cloud.openfeign.client.config.default}), now for this
 * service's one WebClient. A genuinely external, un-registered third party
 * has no Eureka-backed load balancer or client-side retry layer standing
 * between this service and it — without an explicit bound here, a hanging
 * exchange-rate API would hang the bulkhead thread calling it indefinitely,
 * which is a smaller, contained blast radius than hanging a Tomcat thread
 * (see {@link com.spendwise.transactionservice.client.ExchangeRateClient}),
 * but still a thread never returned to its pool.
 */
@Configuration
public class ExchangeRateWebClientConfig {

    @Bean
    public WebClient exchangeRateWebClient(
            WebClient.Builder builder,
            @Value("${spendwise.exchange-rate.base-url}") String baseUrl,
            @Value("${spendwise.exchange-rate.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${spendwise.exchange-rate.read-timeout-ms}") int readTimeoutMs) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs));

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
