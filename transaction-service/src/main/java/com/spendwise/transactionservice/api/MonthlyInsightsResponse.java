package com.spendwise.transactionservice.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * The home screen's "This month" card: what came in, what went out, where it
 * went (by category, with each category's share of the month's spending) and
 * the five UPI merchants the user paid most.
 */
public record MonthlyInsightsResponse(
        String userId,
        String month,
        BigDecimal totalSpent,
        BigDecimal totalIncome,
        long expenseCount,
        List<CategorySpend> categories,
        List<MerchantSpend> topMerchants) {

    public record CategorySpend(String category, BigDecimal amount, long count, BigDecimal sharePercent) {
    }

    public record MerchantSpend(String merchant, BigDecimal amount, long count) {
    }
}
