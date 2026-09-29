package com.spendwise.budgetservice.mapper;

import com.spendwise.budgetservice.api.BudgetResponse;
import com.spendwise.budgetservice.domain.Budget;
import org.mapstruct.Mapper;

/**
 * Compile-time-checked entity-to-response-DTO mapping (Milestone 8), replacing
 * the hand-written {@code BudgetService.toResponse(Budget)} this milestone
 * removes. A 1:1 field mapping — every {@link BudgetResponse} component has a
 * same-named {@link Budget} accessor, so no {@code @Mapping} is needed.
 */
@Mapper(componentModel = "spring")
public interface BudgetMapper {

    BudgetResponse toResponse(Budget budget);
}
