package com.spendwise.budgetservice.api;

import com.spendwise.budgetservice.domain.BudgetEnforcement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * No {@code userId}: a budget always belongs to the caller (the token's
 * {@code sub}), never to an id the client chooses.
 *
 * @param enforcement Milestone 21 — optional; {@code null} means
 *                    {@link BudgetEnforcement#SOFT}, so clients written before
 *                    this field existed keep working unchanged (Q64).
 */
public record CreateBudgetRequest(
        @NotBlank @Size(max = 100) String category,
        @NotNull @Positive BigDecimal cappedAmount,
        @NotNull YearMonth periodMonth,
        BudgetEnforcement enforcement) {

    public BudgetEnforcement enforcementOrDefault() {
        return enforcement == null ? BudgetEnforcement.SOFT : enforcement;
    }
}
