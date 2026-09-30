package com.spendwise.budgetservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * {@code @EnableFeignClients} (Milestone 10) must be declared explicitly on
 * the consuming service's main class — unlike the Eureka client, which
 * auto-configures the moment {@code spring-cloud-starter-netflix-eureka-client}
 * is on the classpath, Spring Cloud OpenFeign requires this annotation to
 * trigger classpath scanning for {@code @FeignClient}-annotated interfaces
 * (here, {@link com.spendwise.budgetservice.client.UserServiceClient}).
 */
@EnableFeignClients
@SpringBootApplication
public class BudgetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BudgetServiceApplication.class, args);
    }
}
