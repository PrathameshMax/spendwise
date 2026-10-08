package com.spendwise.budgetservice.service;

import com.spendwise.budgetservice.api.BudgetResponse;
import com.spendwise.budgetservice.api.CreateBudgetRequest;
import com.spendwise.budgetservice.client.UserServiceClient;
import com.spendwise.budgetservice.domain.Budget;
import com.spendwise.budgetservice.domain.BudgetRepository;
import com.spendwise.budgetservice.mapper.BudgetMapper;
import com.spendwise.common.exception.DuplicateResourceException;
import com.spendwise.common.exception.ResourceNotFoundException;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetMapper budgetMapper;
    private final UserServiceClient userServiceClient;

    public BudgetService(BudgetRepository budgetRepository,
                          BudgetMapper budgetMapper,
                          UserServiceClient userServiceClient) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
        this.userServiceClient = userServiceClient;
    }

    @Transactional
    public BudgetResponse create(CreateBudgetRequest request) {
        verifyUserExists(request.userId());

        if (budgetRepository.existsByUserIdAndCategoryAndPeriodMonth(
                request.userId(), request.category(), request.periodMonth())) {
            throw new DuplicateResourceException(
                    "Budget", "userId+category+periodMonth",
                    "%s/%s/%s".formatted(request.userId(), request.category(), request.periodMonth()));
        }

        Budget saved = budgetRepository.save(new Budget(
                request.userId(), request.category(), request.cappedAmount(), request.periodMonth(),
                request.enforcementOrDefault()));
        return budgetMapper.toResponse(saved);
    }

    /**
     * Milestone 10 — the platform's first real synchronous inter-service
     * call: before a budget can be created against a userId, that user must
     * actually exist in user-service. Feign's default error decoder throws
     * {@link FeignException.NotFound} for any 404 response, which is
     * translated here into this service's own {@link ResourceNotFoundException}
     * so the caller sees the same RFC 7807 shape (Milestone 7) it would for
     * any other not-found resource, regardless of whether the check happened
     * locally or over the network.
     */
    private void verifyUserExists(UUID userId) {
        try {
            userServiceClient.getById(userId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("UserProfile", userId);
        }
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> summary(UUID userId, YearMonth periodMonth) {
        return budgetRepository.findByUserIdAndPeriodMonth(userId, periodMonth).stream()
                .map(budgetMapper::toResponse)
                .toList();
    }
}
