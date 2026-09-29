package com.spendwise.transactionservice.mapper;

import com.spendwise.transactionservice.api.CategoryResponse;
import com.spendwise.transactionservice.domain.Category;
import org.mapstruct.Mapper;

/**
 * Compile-time-checked entity-to-response-DTO mapping (Milestone 8), replacing
 * the hand-written {@code CategoryService.toResponse(Category)} this milestone
 * removes. A 1:1 field mapping — {@code Category.systemDefault} maps to
 * {@code CategoryResponse.systemDefault} by MapStruct's own JavaBean-property
 * convention for a boolean {@code isSystemDefault()} accessor, with no
 * {@code @Mapping} needed.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
