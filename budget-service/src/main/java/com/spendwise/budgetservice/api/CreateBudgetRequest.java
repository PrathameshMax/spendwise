package com.spendwise.budgetservice.api;

import com.spendwise.budgetservice.domain.BudgetEnforcement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * @param enforcement Milestone 21 — optional; {@code null} means
 *                    {@link BudgetEnforcement#SOFT}, so clients written before
 *                    this field existed keep working unchanged (Q64).
 */
public record CreateBudgetRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 100) String category,
        @NotNull @Positive BigDecimal cappedAmount,
        @NotNull YearMonth periodMonth,
        BudgetEnforcement enforcement) {

    public BudgetEnforcement enforcementOrDefault() {
        return enforcement == null ? BudgetEnforcement.SOFT : enforcement;
    }
}
