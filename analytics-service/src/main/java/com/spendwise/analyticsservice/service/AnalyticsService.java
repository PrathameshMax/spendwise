package com.spendwise.analyticsservice.service;

import com.spendwise.analyticsservice.api.BudgetSummaryItemResponse;
import com.spendwise.analyticsservice.api.DashboardResponse;
import com.spendwise.analyticsservice.domain.DashboardQueryLog;
import com.spendwise.analyticsservice.domain.DashboardQueryLogRepository;
import com.spendwise.analyticsservice.grpc.BudgetSummaryGrpcClient;
import com.spendwise.grpc.budget.BudgetSummaryItem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Milestone 15 — composes the one non-blocking call chain this service
 * exists to demonstrate: fetch a user's budget summary from budget-service
 * over gRPC, then record that the dashboard was queried, via a genuinely
 * separate, non-blocking write to this service's own {@code analytics_db}.
 * Both steps complete, end to end, without ever parking a thread on I/O —
 * the gRPC call via {@link BudgetSummaryGrpcClient}'s async-stub bridge, the
 * database write via Spring Data R2DBC.
 *
 * <p>The query-log write is sequenced strictly after the gRPC call succeeds
 * ({@code flatMap}, not a parallel {@code zip}): logging a query that never
 * actually returned data would make {@code dashboard_query_log} record
 * requests this service never genuinely served, undermining the one thing
 * that table exists to be an honest record of.
 */
@Service
public class AnalyticsService {

    private final BudgetSummaryGrpcClient budgetSummaryGrpcClient;
    private final DashboardQueryLogRepository dashboardQueryLogRepository;

    public AnalyticsService(BudgetSummaryGrpcClient budgetSummaryGrpcClient,
                             DashboardQueryLogRepository dashboardQueryLogRepository) {
        this.budgetSummaryGrpcClient = budgetSummaryGrpcClient;
        this.dashboardQueryLogRepository = dashboardQueryLogRepository;
    }

    public Mono<DashboardResponse> getDashboard(UUID userId, YearMonth periodMonth) {
        return budgetSummaryGrpcClient.getSummary(userId, periodMonth)
                .map(items -> items.stream().map(this::toResponse).toList())
                .flatMap(items -> dashboardQueryLogRepository
                        .save(DashboardQueryLog.newEntry(userId, periodMonth, items.size()))
                        .thenReturn(new DashboardResponse(userId, periodMonth, items)));
    }

    private BudgetSummaryItemResponse toResponse(BudgetSummaryItem item) {
        return new BudgetSummaryItemResponse(
                UUID.fromString(item.getId()),
                UUID.fromString(item.getUserId()),
                item.getCategory(),
                new BigDecimal(item.getCappedAmount()),
                new BigDecimal(item.getCurrentSpend()),
                YearMonth.parse(item.getPeriodMonth()),
                Instant.parse(item.getCreatedAt()));
    }
}
