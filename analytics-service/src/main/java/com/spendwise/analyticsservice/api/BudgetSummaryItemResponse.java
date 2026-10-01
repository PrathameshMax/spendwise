package com.spendwise.analyticsservice.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Milestone 15 — the REST-facing re-typing of the gRPC wire contract's
 * {@code BudgetSummaryItem} (all-string fields, per that .proto's own
 * rationale): {@code AnalyticsService} converts each gRPC item's string
 * fields back into proper Java types once, at the service boundary, so
 * {@code AnalyticsController}'s JSON response carries the same typed shape
 * every other REST endpoint on this platform already does — a caller of this
 * endpoint has no reason to know, or care, that the data crossed an internal
 * gRPC hop on its way here.
 */
public record BudgetSummaryItemResponse(
        UUID id,
        UUID userId,
        String category,
        BigDecimal cappedAmount,
        BigDecimal currentSpend,
        YearMonth periodMonth,
        Instant createdAt) {
}
