package com.spendwise.budgetservice.messaging;

/**
 * Milestone 21 — what {@link BudgetSpendService} did with one
 * {@code TransactionCreatedEvent}; the {@code outcome} tag of
 * {@code spendwise.saga.budget.decisions}.
 */
public enum BudgetDecision {

    /** Expense added to the matching budget's current spend. */
    APPLIED("applied"),

    /** Expense would breach a HARD cap: not applied, TransactionRejectedEvent published. */
    REJECTED("rejected"),

    /** No budget exists for this user, category and month; nothing to enforce. */
    NO_BUDGET("no_budget"),

    /** Income, which never counts against a budget. */
    NOT_EXPENSE("not_expense"),

    /** Event has no category name (published before Milestone 21); cannot be matched to a budget. */
    UNATTRIBUTABLE("unattributable"),

    /** Already processed by this consumer; discarded by the idempotency guard. */
    DUPLICATE("duplicate");

    private final String tag;

    BudgetDecision(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
