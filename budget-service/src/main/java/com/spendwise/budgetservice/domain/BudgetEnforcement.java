package com.spendwise.budgetservice.domain;

/**
 * Milestone 21 — how a budget's cap is enforced once spend would cross it
 * (Functional Roadmap, Workflow 2, Step 2).
 * <ul>
 *   <li>{@link #SOFT} — an alert threshold. The expense is applied and spend
 *   may exceed the cap. The default, and the behaviour of every budget created
 *   before this milestone (V2 migration backfills it).</li>
 *   <li>{@link #HARD} — a "do-not-exceed" ceiling. An expense that would take
 *   spend past the cap is not applied; budget-service emits a compensating
 *   {@code TransactionRejectedEvent} and transaction-service reverses the
 *   ledger entry.</li>
 * </ul>
 */
public enum BudgetEnforcement {
    SOFT,
    HARD
}
