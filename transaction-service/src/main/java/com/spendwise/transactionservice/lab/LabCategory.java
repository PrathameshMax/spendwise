package com.spendwise.transactionservice.lab;

import java.util.UUID;

/**
 * Categories the lab generator books transactions against. When a category
 * with the same name already exists (created through the real API), the lab
 * reuses it; otherwise it is created with a lab-range id from {@link LabIds}
 * and removed again by {@code DELETE /lab/seed} once nothing references it.
 */
public enum LabCategory {

    SALARY("Salary"),
    REFUNDS("Refunds & Cashback"),
    RENT("Rent"),
    LOAN_EMI("Loan EMI"),
    INVESTMENTS("Investments"),
    BILLS("Bills & Utilities"),
    FOOD("Food & Dining"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    FUEL("Fuel"),
    SHOPPING("Shopping"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    TRAVEL("Travel"),
    CASH("Cash Withdrawal");

    private final String displayName;

    LabCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public UUID labId() {
        return LabIds.categoryId(ordinal());
    }
}
