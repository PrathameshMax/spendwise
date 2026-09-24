package com.spendwise.budgetservice.service;

import com.spendwise.budgetservice.api.BudgetResponse;
import com.spendwise.budgetservice.api.CreateBudgetRequest;
import com.spendwise.budgetservice.domain.Budget;
import com.spendwise.budgetservice.domain.BudgetRepository;
import com.spendwise.common.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public BudgetResponse create(CreateBudgetRequest request) {
        if (budgetRepository.existsByUserIdAndCategoryAndPeriodMonth(
                request.userId(), request.category(), request.periodMonth())) {
            throw new DuplicateResourceException(
                    "Budget", "userId+category+periodMonth",
                    "%s/%s/%s".formatted(request.userId(), request.category(), request.periodMonth()));
        }

        Budget saved = budgetRepository.save(new Budget(
                request.userId(), request.category(), request.cappedAmount(), request.periodMonth()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> summary(UUID userId, YearMonth periodMonth) {
        return budgetRepository.findByUserIdAndPeriodMonth(userId, periodMonth).stream()
                .map(this::toResponse)
                .toList();
    }

    private BudgetResponse toResponse(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getUserId(),
                budget.getCategory(),
                budget.getCappedAmount(),
                budget.getCurrentSpend(),
                budget.getPeriodMonth(),
                budget.getCreatedAt());
    }
}
