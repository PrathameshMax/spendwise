package com.spendwise.transactionservice.client;

import com.spendwise.common.exception.DownstreamServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Milestone 13 — the platform's first genuine third-party integration
 * (Functional Roadmap: "a resilient WebClient call to the external
 * exchange-rate API"). WebClient, not Feign/{@code @FeignClient}: Feign's
 * whole purpose in this codebase (see {@code UserServiceClient}'s Javadoc) is
 * resolving a sibling service's logical name against the Eureka registry —
 * this target is a real external host with no Eureka registration and no
 * platform-owned contract to declare.
 *
 * <p>The call is deliberately <b>blocking</b> ({@link WebClient}'s reactive
 * chain is terminated with {@code .block()} right here), not composed as a
 * {@code Mono} the caller subscribes to: this whole service is a Spring MVC/
 * Tomcat application (every other milestone's resilience work has assumed
 * that thread-per-request model), and {@code TransactionService.create} is a
 * synchronous, {@code @Transactional} method — turning one call inside it
 * reactive while the rest of the method stays blocking buys nothing and
 * reintroduces the exact "slow third party starves my pool" risk the roadmap
 * calls this milestone out to fix, just moved from Tomcat's request-handling
 * pool onto whatever thread happens to run the reactive chain.
 *
 * <p>{@code type = Bulkhead.Type.THREADPOOL} is what actually solves that:
 * Resilience4j's {@code ThreadPoolBulkheadAspect} submits this ENTIRE method
 * body to the bulkhead's own, separately-sized thread pool (see
 * {@code application.yml}'s {@code resilience4j.bulkhead.thread-pool
 * -instances.exchangeRateLookup}) rather than running it on whatever thread
 * called in — confirmed correct against the Resilience4j maintainers'
 * own guidance ({@code CompletableFuture.completedFuture(blockingCall())} is
 * the documented, supported shape for a {@code THREADPOOL}-type annotation;
 * no manual {@code supplyAsync}/executor is needed, unlike
 * {@code AsyncUserServiceLookup}'s {@code @TimeLimiter} below, which has no
 * thread pool of its own and genuinely needs one). A slow or hanging exchange
 * -rate API can therefore never exhaust the same Tomcat thread pool that's
 * serving every other request this service handles — it can only exhaust its
 * own small, dedicated, bounded pool, and once that pool and its queue are
 * full, {@code BulkheadFullException} routes straight to the fallback instead
 * of piling up more waiting threads.
 */
@Component
public class ExchangeRateClient {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeRateClient.class);

    private final WebClient exchangeRateWebClient;

    public ExchangeRateClient(WebClient exchangeRateWebClient) {
        this.exchangeRateWebClient = exchangeRateWebClient;
    }

    @Bulkhead(name = "exchangeRateLookup", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "getConversionRateFallback")
    public CompletableFuture<BigDecimal> getConversionRate(String fromCurrency, String toCurrency) {
        ExchangeRateApiResponse response = exchangeRateWebClient.get()
                .uri("/v6/latest/{base}", fromCurrency)
                .retrieve()
                .bodyToMono(ExchangeRateApiResponse.class)
                .block();

        if (response == null || response.rates() == null || !response.rates().containsKey(toCurrency)) {
            throw new IllegalStateException(
                    "exchange-rate API response did not include a rate for " + fromCurrency + " -> " + toCurrency);
        }

        return CompletableFuture.completedFuture(response.rates().get(toCurrency));
    }

    /**
     * Reached when the bulkhead's pool (and its queue) is full
     * ({@code BulkheadFullException}), the WebClient call itself failed
     * (connect/read timeout, non-2xx status, I/O error), or the response
     * didn't contain the requested currency. All three are equally "this
     * platform cannot tell you the conversion rate right now" from the
     * caller's point of view — reusing {@link DownstreamServiceUnavailableException}
     * rather than inventing an exchange-rate-specific type, exactly as that
     * exception's own Javadoc anticipated.
     */
    private CompletableFuture<BigDecimal> getConversionRateFallback(
            String fromCurrency, String toCurrency, Throwable throwable) {
        LOG.warn("exchange-rate lookup fell back for {} -> {}: {}", fromCurrency, toCurrency, throwable.toString());
        throw new DownstreamServiceUnavailableException("exchange-rate-service", throwable);
    }
}
