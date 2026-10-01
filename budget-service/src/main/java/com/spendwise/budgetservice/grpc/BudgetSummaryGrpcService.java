package com.spendwise.budgetservice.grpc;

import com.spendwise.budgetservice.api.BudgetResponse;
import com.spendwise.budgetservice.service.BudgetService;
import com.spendwise.grpc.budget.BudgetSummaryItem;
import com.spendwise.grpc.budget.BudgetSummaryRequest;
import com.spendwise.grpc.budget.BudgetSummaryResponse;
import com.spendwise.grpc.budget.BudgetSummaryServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Milestone 15 — the gRPC mirror of {@code BudgetController#summary}
 * (REST {@code GET /api/v1/budgets/{userId}/summary}), serving the same
 * {@link BudgetService#summary(UUID, YearMonth)} query over a second,
 * binary-framed transport for the roadmap's "low-latency, high-throughput
 * internal query" use case and the REST-vs-gRPC benchmark (README's
 * Milestone 15 Chaos Lab).
 *
 * <p>{@code @GrpcService} (net.devh) is this annotation's exact gRPC-server
 * analogue of {@code @RestController}: it registers this bean's service
 * definition onto the Netty-based gRPC server net.devh's starter bootstraps
 * on {@code grpc.server.port} (config-repo/budget-service.yml), entirely
 * separate from Tomcat's own port 8084 — no {@code @Enable...} annotation is
 * needed on {@link com.spendwise.budgetservice.BudgetServiceApplication},
 * unlike Spring Cloud OpenFeign's {@code @EnableFeignClients}.
 *
 * <p>Deliberately delegates to the existing {@link BudgetService}, the same
 * service bean {@code BudgetController} itself calls — this endpoint is a
 * second transport over identical business logic, not a parallel
 * implementation of it, so the two transports can never drift in behavior.
 *
 * <p>Error handling intentionally does not reuse
 * {@link com.spendwise.budgetservice.config.GlobalExceptionHandler}: that
 * class's {@code @RestControllerAdvice} machinery is Spring MVC's own
 * {@code HandlerExceptionResolver} infrastructure, which a gRPC call never
 * passes through — a gRPC server reports failure via {@link Status} codes
 * on the {@link StreamObserver}, not an HTTP response body, so this class
 * maps the one real failure mode this endpoint has (an unparseable
 * {@code period_month}) directly to {@link Status#INVALID_ARGUMENT} itself.
 */
@GrpcService
public class BudgetSummaryGrpcService extends BudgetSummaryServiceGrpc.BudgetSummaryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BudgetSummaryGrpcService.class);

    private final BudgetService budgetService;

    public BudgetSummaryGrpcService(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public void getSummary(BudgetSummaryRequest request, StreamObserver<BudgetSummaryResponse> responseObserver) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("user_id must be a well-formed UUID: " + request.getUserId())
                    .withCause(ex)
                    .asRuntimeException());
            return;
        }

        YearMonth periodMonth;
        try {
            periodMonth = YearMonth.parse(request.getPeriodMonth());
        } catch (DateTimeParseException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("period_month must be formatted yyyy-MM: " + request.getPeriodMonth())
                    .withCause(ex)
                    .asRuntimeException());
            return;
        }

        try {
            BudgetSummaryResponse.Builder response = BudgetSummaryResponse.newBuilder();
            for (BudgetResponse budget : budgetService.summary(userId, periodMonth)) {
                response.addItems(toGrpcItem(budget));
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            log.error("Unhandled exception serving BudgetSummaryService.getSummary for userId={}", userId, ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("An unexpected error occurred")
                    .withCause(ex)
                    .asRuntimeException());
        }
    }

    /**
     * {@code BigDecimal#toPlainString()}, never {@code toString()}: a
     * {@link java.math.BigDecimal} loaded from Postgres {@code numeric(19,2)}
     * can carry an exponent-form scale {@code toString()} would render as
     * scientific notation for sufficiently large values (e.g. {@code 1E+3}) —
     * {@code toPlainString()} always renders the unscaled decimal form
     * ("1000.00"), matching exactly what Jackson already serializes for this
     * same field on the REST side, keeping the two transports' payloads
     * byte-for-byte comparable in the benchmark.
     */
    private BudgetSummaryItem toGrpcItem(BudgetResponse budget) {
        return BudgetSummaryItem.newBuilder()
                .setId(budget.id().toString())
                .setUserId(budget.userId().toString())
                .setCategory(budget.category())
                .setCappedAmount(budget.cappedAmount().toPlainString())
                .setCurrentSpend(budget.currentSpend().toPlainString())
                .setPeriodMonth(budget.periodMonth().toString())
                .setCreatedAt(budget.createdAt().toString())
                .build();
    }
}
