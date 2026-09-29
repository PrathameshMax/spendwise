package com.spendwise.budgetservice.service;

import com.spendwise.budgetservice.api.BudgetResponse;
import com.spendwise.budgetservice.api.CreateBudgetRequest;
import com.spendwise.budgetservice.domain.Budget;
import com.spendwise.budgetservice.domain.BudgetRepository;
import com.spendwise.budgetservice.mapper.BudgetMapper;
import com.spendwise.common.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetRepository budgetRepository, BudgetMapper budgetMapper) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
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
        return budgetMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> summary(UUID userId, YearMonth periodMonth) {
        return budgetRepository.findByUserIdAndPeriodMonth(userId, periodMonth).stream()
                .map(budgetMapper::toResponse)
                .toList();
    }
}
