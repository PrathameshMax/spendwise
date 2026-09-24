package com.spendwise.transactionservice.service;

import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.transactionservice.api.CreateTransactionRequest;
import com.spendwise.transactionservice.api.TransactionFilter;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.domain.Category;
import com.spendwise.transactionservice.domain.CategoryRepository;
import com.spendwise.transactionservice.domain.Transaction;
import com.spendwise.transactionservice.domain.TransactionRepository;
import com.spendwise.transactionservice.domain.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        Transaction saved = transactionRepository.save(new Transaction(
                request.userId(),
                category,
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate()));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(TransactionFilter filter, Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecifications.combine(
                TransactionSpecifications.hasUserId(filter.userId()),
                TransactionSpecifications.hasCategoryId(filter.categoryId()),
                TransactionSpecifications.hasType(filter.type()),
                TransactionSpecifications.dateFrom(filter.fromDate()),
                TransactionSpecifications.dateTo(filter.toDate()),
                TransactionSpecifications.amountFrom(filter.minAmount()),
                TransactionSpecifications.amountTo(filter.maxAmount()));

        return transactionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getCreatedAt());
    }
}
