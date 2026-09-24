package com.spendwise.budgetservice.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record CreateBudgetRequest(UUID userId, String category, BigDecimal cappedAmount, YearMonth periodMonth) {
}
