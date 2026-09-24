package com.spendwise.budgetservice.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID userId,
        String category,
        BigDecimal cappedAmount,
        BigDecimal currentSpend,
        YearMonth periodMonth,
        Instant createdAt) {
}
