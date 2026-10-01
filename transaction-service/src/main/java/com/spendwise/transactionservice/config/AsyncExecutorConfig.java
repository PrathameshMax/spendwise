package com.spendwise.transactionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Milestone 13 — a dedicated, bounded pool for
 * {@code AsyncUserServiceLookup}'s {@code CompletableFuture.supplyAsync}
 * wrapper around the (otherwise synchronous) {@code UserServiceClient.getById}
 * call. Deliberately a hand-sized pool rather than
 * {@code ForkJoinPool.commonPool()} (what bare {@code supplyAsync(supplier)}
 * uses when no executor is given): the common pool is a process-wide shared
 * resource other unrelated async work in the JVM (parallel streams, anything
 * else that defaults to it) also draws from, so a slow/blocked user-service
 * lookup competing for it could starve work that has nothing to do with this
 * call — precisely the "pool starvation" failure mode this whole milestone is
 * about, just one layer removed from Tomcat's own thread pool. This is a
 * smaller-scale version of exactly what {@link ExchangeRateClient}'s
 * ThreadPoolBulkhead gets automatically; the difference here is that
 * Resilience4j's {@code @TimeLimiter} annotation has no thread pool
 * mechanism of its own (see {@code AsyncUserServiceLookup}'s Javadoc) — it
 * only races a timeout against whatever {@code CompletableFuture} it's
 * handed, so this executor has to be provided by hand.
 */
@Configuration
public class AsyncExecutorConfig {

    @Bean
    public Executor userServiceLookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("user-svc-lookup-");
        executor.initialize();
        return executor;
    }
}
