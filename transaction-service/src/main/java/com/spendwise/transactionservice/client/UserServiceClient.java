package com.spendwise.transactionservice.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Milestone 10 — transaction-service's own Feign contract against
 * user-service's existing {@code GET /api/v1/users/{id}} endpoint. The
 * {@code name} attribute is user-service's {@code spring.application.name}
 * (its Eureka registration id, not a hostname): Spring Cloud OpenFeign
 * resolves it against the registry {@code discovery-server} maintains and
 * client-side load-balances across whatever instances are currently
 * registered under it, replacing what would otherwise be a hardcoded
 * {@code http://user-service:8082} URL (Q41/Q42).
 *
 * <p>Declared here rather than in spendwise-common because this contract is
 * business-domain-specific (it names a concrete service and a concrete
 * endpoint) — spendwise-common carries zero business logic, so
 * budget-service declares its own identical-looking copy of this interface
 * rather than sharing this one.
 *
 * <p>{@code @Retry(name = "userServiceLookup")} (Milestone 11) is placed on
 * this interface method, not on the {@code verifyUserExists} call site in
 * {@code TransactionService} — Resilience4j's annotation support is
 * Spring-AOP-proxy-based, so it only intercepts a call that arrives from
 * outside the bean; {@code verifyUserExists} calls this method externally
 * (crossing the Feign client bean's proxy boundary), which is exactly what
 * makes the annotation effective here. The named instance's policy
 * (exponential backoff + jitter, narrowed to {@code feign.RetryableException}
 * only) is centralized in config-repo/application.yml alongside this
 * platform's Feign connect/read timeouts, both being generic Feign
 * infrastructure concerns rather than anything specific to this contract.
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    @Retry(name = "userServiceLookup")
    @GetMapping("/api/v1/users/{id}")
    UserExistenceResponse getById(@PathVariable("id") UUID id);
}
