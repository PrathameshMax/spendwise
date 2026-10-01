package com.spendwise.transactionservice.client;

import com.spendwise.common.exception.DownstreamServiceUnavailableException;
import com.spendwise.common.exception.SpendWiseException;
import feign.FeignException;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Milestone 13 — "a TimeLimiter wrapping an async CompletableFuture-based
 * Feign call." Spring Cloud OpenFeign's {@code @FeignClient} interfaces are
 * purely synchronous/blocking by design (Feign has no reactive client
 * support, and Spring Cloud OpenFeign explicitly defers to the separate
 * {@code feign-reactive} project rather than supporting it itself) — a
 * {@code @FeignClient} method cannot simply declare
 * {@code CompletableFuture<T>} as its return type; Resilience4j's own
 * {@code @TimeLimiter} annotation, applied directly to such a method, fails
 * at runtime with "Return type not supported" (confirmed against
 * resilience4j/resilience4j#1111).
 *
 * <p>This class is the documented, supported way around that: a separate
 * Spring bean wraps the existing, unmodified, Retry+CircuitBreaker-protected
 * {@link UserServiceClient#getById} call in a genuinely asynchronous
 * {@code CompletableFuture.supplyAsync}, on its own dedicated
 * {@code userServiceLookupExecutor} (not the shared {@code ForkJoinPool}
 * common pool — see {@code AsyncExecutorConfig}'s Javadoc), and applies
 * {@code @TimeLimiter} to <b>this</b> method instead. Calling
 * {@code userServiceClient.getById(id)} from here still crosses that Feign
 * client bean's own Spring AOP proxy boundary exactly as it would from any
 * other bean (interception is proxy-based, not thread-based — see
 * {@code UserServiceClient}'s own Javadoc), so its {@code @Retry} and
 * {@code @CircuitBreaker} keep working completely unchanged underneath this.
 *
 * <p>Composed this way, {@code @TimeLimiter} ends up as the OUTERMOST layer
 * around the entire Retry+CircuitBreaker-protected call — bounding the total
 * wall-clock time of however many retry attempts happen, not just one
 * attempt's own read-timeout (M13's Q50 goes through why that's the
 * deliberate choice here, not an accident of where the annotation landed).
 */
@Component
public class AsyncUserServiceLookup {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncUserServiceLookup.class);

    private final UserServiceClient userServiceClient;
    private final Executor userServiceLookupExecutor;

    public AsyncUserServiceLookup(UserServiceClient userServiceClient, Executor userServiceLookupExecutor) {
        this.userServiceClient = userServiceClient;
        this.userServiceLookupExecutor = userServiceLookupExecutor;
    }

    @TimeLimiter(name = "userServiceLookup", fallbackMethod = "getByIdAsyncFallback")
    public CompletableFuture<UserExistenceResponse> getByIdAsync(UUID id) {
        return CompletableFuture.supplyAsync(() -> userServiceClient.getById(id), userServiceLookupExecutor);
    }

    /**
     * By the time any exception reaches here, {@code userServiceClient
     * .getById}'s own fallback has already run and already made the
     * NotFound-vs-genuinely-unavailable distinction (the Milestone 12 fix) —
     * this method only needs to let that decision through unchanged, not
     * remake it. The one new failure mode introduced here is
     * {@link java.util.concurrent.TimeoutException} from {@code @TimeLimiter}
     * itself, when the whole Retry+CircuitBreaker sequence didn't finish
     * inside {@code resilience4j.timelimiter.instances.userServiceLookup
     * .timeout-duration} — that is exactly the kind of "can't tell you right
     * now" {@code UserServiceClient.getByIdFallback} already converts to
     * {@code DownstreamServiceUnavailableException} for every other failure
     * reason, so this method converts it the same way rather than
     * introducing a second, inconsistent shape for the same outcome.
     *
     * <p>Defensively unwraps one level of {@link CompletionException} before
     * checking the throwable's type: Resilience4j's own documentation does
     * not pin down whether a fallback for a {@code CompletionStage}-returning
     * method always receives the raw cause or can receive it wrapped, and
     * this platform would rather check both shapes than risk this exact
     * failure mode again — a {@code FeignException.NotFound} silently
     * surviving unrecognized inside a wrapper and being misclassified as
     * "service unavailable," the precise bug the Milestone 12 fix exists to
     * prevent one layer down.
     */
    private CompletableFuture<UserExistenceResponse> getByIdAsyncFallback(UUID id, Throwable throwable) {
        Throwable actual = (throwable instanceof CompletionException && throwable.getCause() != null)
                ? throwable.getCause()
                : throwable;

        if (actual instanceof FeignException.NotFound notFound) {
            throw notFound;
        }
        if (actual instanceof SpendWiseException spendWiseException) {
            // UserServiceClient.getByIdFallback already produced a well-formed,
            // type-safe outcome (e.g. DownstreamServiceUnavailableException) —
            // pass it through rather than wrapping a wrapper.
            throw spendWiseException;
        }

        LOG.warn("async user-service lookup fell back for userId={}: {}", id, actual.toString());
        throw new DownstreamServiceUnavailableException("user-service", actual);
    }
}
