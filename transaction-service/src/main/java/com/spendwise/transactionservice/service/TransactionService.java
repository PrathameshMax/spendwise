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
import com.spendwise.transactionservice.mapper.TransactionMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository,
                               CategoryRepository categoryRepository,
                               TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionMapper = transactionMapper;
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

        return transactionMapper.toResponse(saved);
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

        return transactionRepository.findAll(spec, pageable).map(transactionMapper::toResponse);
    }
}
