package com.spendwise.budgetservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    boolean existsByUserIdAndCategoryAndPeriodMonth(UUID userId, String category, YearMonth periodMonth);

    List<Budget> findByUserIdAndPeriodMonth(UUID userId, YearMonth periodMonth);
}
