package com.spendwise.budgetservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * category is a plain denormalized string, not a foreign key into Transaction
 * Service's categories table — budget_db must never depend on another service's
 * schema. currentSpend starts at zero and is only ever mutated by the Kafka-driven
 * consumer, {@code BudgetSpendService} (Milestone 21), through
 * {@link #applySpend(BigDecimal)}.
 *
 * <p>Milestone 21 — {@link #enforcement} decides what happens when an expense
 * would take spend past the cap: {@link #wouldBreachHardCap(BigDecimal)} is the
 * single rule the saga's compensation hinges on.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String category;

    @Column(name = "capped_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cappedAmount;

    @Column(name = "current_spend", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentSpend;

    @Column(name = "period_month", nullable = false, length = 7)
    private YearMonth periodMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BudgetEnforcement enforcement;

    protected Budget() {
        // required by JPA
    }

    public Budget(UUID userId, String category, BigDecimal cappedAmount, YearMonth periodMonth) {
        this(userId, category, cappedAmount, periodMonth, BudgetEnforcement.SOFT);
    }

    public Budget(UUID userId, String category, BigDecimal cappedAmount, YearMonth periodMonth,
                  BudgetEnforcement enforcement) {
        this.userId = userId;
        this.category = category;
        this.cappedAmount = cappedAmount;
        this.currentSpend = BigDecimal.ZERO;
        this.periodMonth = periodMonth;
        this.enforcement = enforcement;
        this.createdAt = Instant.now();
    }

    /**
     * Milestone 21 — {@code true} only for a HARD budget whose spend would
     * end up strictly above the cap: landing exactly on the cap is allowed.
     * A SOFT budget never breaches; it is allowed to run over.
     */
    public boolean wouldBreachHardCap(BigDecimal expense) {
        return enforcement == BudgetEnforcement.HARD
                && currentSpend.add(expense).compareTo(cappedAmount) > 0;
    }

    /**
     * Milestone 21 — adds an accepted expense to this month's spend. The
     * entity is managed inside the consumer's transaction, so the change is
     * flushed as an UPDATE at commit.
     */
    public void applySpend(BigDecimal expense) {
        this.currentSpend = this.currentSpend.add(expense);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getCappedAmount() {
        return cappedAmount;
    }

    public BigDecimal getCurrentSpend() {
        return currentSpend;
    }

    public YearMonth getPeriodMonth() {
        return periodMonth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BudgetEnforcement getEnforcement() {
        return enforcement;
    }
}
