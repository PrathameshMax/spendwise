package com.spendwise.analyticsservice.grpc;

import com.spendwise.common.tracing.CorrelationIdConstants;
import com.spendwise.grpc.budget.BudgetSummaryItem;
import com.spendwise.grpc.budget.BudgetSummaryRequest;
import com.spendwise.grpc.budget.BudgetSummaryResponse;
import com.spendwise.grpc.budget.BudgetSummaryServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

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
 */
@Component
public class BudgetSummaryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(BudgetSummaryGrpcClient.class);

    @GrpcClient("budget-service")
    private BudgetSummaryServiceGrpc.BudgetSummaryServiceStub asyncStub;

    public Mono<List<BudgetSummaryItem>> getSummary(UUID userId, YearMonth periodMonth) {
        BudgetSummaryRequest request = BudgetSummaryRequest.newBuilder()
                .setUserId(userId.toString())
                .setPeriodMonth(periodMonth.toString())
                .build();

        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationIdConstants.MDC_KEY, null);
            return Mono.<BudgetSummaryResponse>create(sink -> asyncStub.getSummary(request, new StreamObserver<>() {
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
        }).map(BudgetSummaryResponse::getItemsList);
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
