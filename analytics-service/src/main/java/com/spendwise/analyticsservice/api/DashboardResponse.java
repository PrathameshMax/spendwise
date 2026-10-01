package com.spendwise.analyticsservice.api;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 15 — the response body of {@code GET /api/v1/analytics/dashboard/{userId}},
 * this service's first real REST surface: a user's budget summary for one
 * period, fetched over gRPC from budget-service rather than queried from any
 * table analytics-service itself owns.
 */
public record DashboardResponse(
        UUID userId,
        YearMonth periodMonth,
        List<BudgetSummaryItemResponse> items) {
}
