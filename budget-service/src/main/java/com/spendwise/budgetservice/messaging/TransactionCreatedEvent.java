package com.spendwise.budgetservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 21 — budget-service's own copy of the payload transaction-service
 * publishes, following the platform's per-consumer contract convention
 * (notification-service and analytics-service each keep their own). A
 * tolerant reader: unknown fields are ignored and {@code type} is a plain
 * {@code String}.
 *
 * <p>{@code categoryName} is new at Milestone 21 — budgets are keyed on the
 * category <em>name</em> (budget_db never references transaction_db's
 * category ids), so transaction-service now includes it. Events published
 * before this milestone have no such field and arrive with {@code null};
 * {@code BudgetSpendService} cannot attribute those to a budget and skips
 * them. This is the backward-compatible shape of a contract change: the
 * producer adds a field, and a consumer reading an old event degrades rather
 * than failing (Q64).
 *
 * @param baseCurrencyAmount the amount in the user's base currency when the
 *                           transaction was recorded in another currency;
 *                           {@code null} when no conversion happened
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        String categoryName,
        BigDecimal amount,
        String type,
        LocalDate transactionDate,
        String currency,
        BigDecimal baseCurrencyAmount) {

    static final String EXPENSE = "EXPENSE";

    /** The amount that counts against a budget: the base-currency figure if converted. */
    BigDecimal budgetAmount() {
        return baseCurrencyAmount != null ? baseCurrencyAmount : amount;
    }
}
