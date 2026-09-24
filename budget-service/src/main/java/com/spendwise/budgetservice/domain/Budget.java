package com.spendwise.budgetservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * consumer introduced in Milestone 18 onward; there is no producer of spend
 * updates yet at this milestone.
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

    protected Budget() {
        // required by JPA
    }

    public Budget(UUID userId, String category, BigDecimal cappedAmount, YearMonth periodMonth) {
        this.userId = userId;
        this.category = category;
        this.cappedAmount = cappedAmount;
        this.currentSpend = BigDecimal.ZERO;
        this.periodMonth = periodMonth;
        this.createdAt = Instant.now();
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
}
