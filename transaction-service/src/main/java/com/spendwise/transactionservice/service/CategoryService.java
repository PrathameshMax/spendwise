package com.spendwise.transactionservice.service;

import com.spendwise.common.exception.DuplicateResourceException;
import com.spendwise.transactionservice.api.CategoryResponse;
import com.spendwise.transactionservice.api.CreateCategoryRequest;
import com.spendwise.transactionservice.domain.Category;
import com.spendwise.transactionservice.domain.CategoryRepository;
import com.spendwise.transactionservice.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Category", "name", request.name());
        }
        Category saved = categoryRepository.save(new Category(request.name(), false));
        return categoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).toList();
    }
}
