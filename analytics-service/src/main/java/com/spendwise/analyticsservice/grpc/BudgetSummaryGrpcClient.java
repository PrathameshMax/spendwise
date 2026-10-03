package com.spendwise.analyticsservice.grpc;

import com.spendwise.common.exception.DownstreamServiceUnavailableException;
import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.grpc.budget.BudgetSummaryItem;
import com.spendwise.grpc.budget.BudgetSummaryRequest;
import com.spendwise.grpc.budget.BudgetSummaryResponse;
import com.spendwise.grpc.budget.BudgetSummaryServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Milestone 15 — bridges budget-service's gRPC {@code BudgetSummaryService}
 * into this service's otherwise fully non-blocking WebFlux call chain.
 *
 * <p>Deliberately injects the <b>async</b> stub
 * ({@link BudgetSummaryServiceGrpc.BudgetSummaryServiceStub}), never the
 * blocking stub: a blocking stub's call parks the calling thread until the
 * response arrives, which — called from a WebFlux request-handling thread —
 * would defeat the entire point of this milestone's reactive rebuild by
 * tying up one of Netty's small, shared event-loop threads for the full
 * round-trip latency of every single dashboard request (Q54/Q55). The async
 * stub instead registers a {@link StreamObserver} callback and returns
 * immediately; {@link Mono#create} adapts that callback-based API into the
 * single-item {@code Mono} the rest of this service composes over, the
 * standard, documented bridge pattern for wrapping a non-reactive
 * callback-based async API in Project Reactor.
 *
 * <p>{@code getItemsList()} is read inside {@code onNext}, not stored and
 * read later: a unary gRPC call's {@code onNext} fires at most once before
 * {@code onCompleted}, so there is exactly one value to hand to
 * {@link reactor.core.publisher.MonoSink#success}.
 *
 * <p>Milestone 17 fix (raised validating Milestone 15) — two gaps closed:
 * <ul>
 *   <li><b>No deadline.</b> gRPC calls have no deadline unless the caller
 *   sets one, so a budget-service that accepted the connection but never
 *   answered left the dashboard request hanging indefinitely. Every call now
 *   gets {@code withDeadlineAfter(...)} ({@code spendwise.grpc.budget-service.deadline},
 *   default 2s). The deadline is applied per call, on a fresh stub view:
 *   {@code withDeadlineAfter} fixes an absolute expiry at the moment it is
 *   called, so applying it once to the injected stub would make every call
 *   after the first two seconds of uptime fail immediately. The deadline also
 *   propagates to the server as the {@code grpc-timeout} header, so
 *   budget-service can stop work the caller has already given up on.</li>
 *   <li><b>Transport failures surfaced as 500.</b> {@code UNAVAILABLE}
 *   (budget-service down or unreachable) and {@code DEADLINE_EXCEEDED} (too
 *   slow) reached {@code GlobalExceptionHandler}'s generic catch-all as an
 *   undifferentiated 500. Both mean "the dependency, not this request, is the
 *   problem — retry later", so both now map to
 *   {@link DownstreamServiceUnavailableException}, which the shared reactive
 *   handler renders as a 503 ProblemDetail, the same contract
 *   transaction-service's Feign fallbacks give for user-service outages.
 *   Every other status code is left as-is: those are request or server bugs,
 *   and disguising them as "temporarily unavailable" would hide them.</li>
 * </ul>
 */
@Component
public class BudgetSummaryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(BudgetSummaryGrpcClient.class);

    private static final String DOWNSTREAM_SERVICE_NAME = "budget-service";

    @GrpcClient("budget-service")
    private BudgetSummaryServiceGrpc.BudgetSummaryServiceStub asyncStub;

    private final Duration deadline;

    public BudgetSummaryGrpcClient(@Value("${spendwise.grpc.budget-service.deadline:2s}") Duration deadline) {
        this.deadline = deadline;
    }

    public Mono<List<BudgetSummaryItem>> getSummary(UUID userId, YearMonth periodMonth) {
        BudgetSummaryRequest request = BudgetSummaryRequest.newBuilder()
                .setUserId(userId.toString())
                .setPeriodMonth(periodMonth.toString())
                .build();

        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationIdConstants.MDC_KEY, null);
            return Mono.<BudgetSummaryResponse>create(sink -> asyncStub
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .getSummary(request, new StreamObserver<>() {
                        @Override
                        public void onNext(BudgetSummaryResponse value) {
                            withCorrelationId(correlationId, () -> log.debug(
                                    "Received {} budget item(s) from budget-service for userId={}, periodMonth={}",
                                    value.getItemsCount(), userId, periodMonth));
                            sink.success(value);
                        }

                        @Override
                        public void onError(Throwable t) {
                            withCorrelationId(correlationId, () -> log.warn(
                                    "budget-service gRPC call failed for userId={}, periodMonth={}: {}",
                                    userId, periodMonth, t.getMessage()));
                            sink.error(t);
                        }

                        @Override
                        public void onCompleted() {
                            // onNext already resolved the sink for this unary call; nothing further to do.
                        }
                    }));
        }).onErrorMap(BudgetSummaryGrpcClient::isDownstreamUnavailable,
                ex -> new DownstreamServiceUnavailableException(DOWNSTREAM_SERVICE_NAME, ex))
                .map(BudgetSummaryResponse::getItemsList);
    }

    /**
     * {@link Status#fromThrowable} walks the cause chain for a
     * {@code StatusRuntimeException}/{@code StatusException}, so this works
     * whichever of the two the stub surfaced, and returns {@code UNKNOWN}
     * (not mapped) for anything that isn't a gRPC status at all.
     */
    private static boolean isDownstreamUnavailable(Throwable ex) {
        Status.Code code = Status.fromThrowable(ex).getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    /**
     * Bridges the correlation id carried in this call's Reactor {@code Context}
     * into the SLF4J MDC for exactly the duration of one log statement running
     * on gRPC's own callback thread — not this request's own Reactor/Netty
     * thread, so Reactor {@code Context} itself (visible only to code inside
     * the reactive chain, not to a callback gRPC invokes directly on its own
     * executor) cannot reach it there. A narrowly-scoped {@code put}-then-
     * {@code remove} bracketing a single synchronous block is safe here in a
     * way a filter-wide {@code MDC.put} is not (see
     * {@code ReactiveCorrelationIdFilter}'s Javadoc for the leak this would
     * otherwise risk): this thread is never left holding a stale value
     * afterward for some unrelated request to pick up.
     */
    private void withCorrelationId(String correlationId, Runnable logStatement) {
        if (correlationId == null) {
            logStatement.run();
            return;
        }
        MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        try {
            logStatement.run();
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }
}
