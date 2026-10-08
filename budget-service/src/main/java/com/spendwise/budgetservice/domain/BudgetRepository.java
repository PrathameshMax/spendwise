package com.spendwise.budgetservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    boolean existsByUserIdAndCategoryAndPeriodMonth(UUID userId, String category, YearMonth periodMonth);

    List<Budget> findByUserIdAndPeriodMonth(UUID userId, YearMonth periodMonth);

    /**
     * Milestone 21 — the one budget a transaction counts against: same user,
     * same category name, same month. At most one exists, by
     * {@code uk_budgets_user_category_period}.
     */
    Optional<Budget> findByUserIdAndCategoryAndPeriodMonth(UUID userId, String category, YearMonth periodMonth);
}
