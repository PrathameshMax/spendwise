package com.spendwise.budgetservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record CreateBudgetRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 100) String category,
        @NotNull @Positive BigDecimal cappedAmount,
        @NotNull YearMonth periodMonth) {
}
