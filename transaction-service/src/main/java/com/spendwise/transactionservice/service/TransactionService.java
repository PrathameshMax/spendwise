package com.spendwise.transactionservice.service;

import com.spendwise.common.exception.ResourceNotFoundException;
import com.spendwise.transactionservice.api.CreateTransactionRequest;
import com.spendwise.transactionservice.api.TransactionFilter;
import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.client.UserServiceClient;
import com.spendwise.transactionservice.domain.Category;
import com.spendwise.transactionservice.domain.CategoryRepository;
import com.spendwise.transactionservice.domain.Transaction;
import com.spendwise.transactionservice.domain.TransactionRepository;
import com.spendwise.transactionservice.domain.TransactionSpecifications;
import com.spendwise.transactionservice.mapper.TransactionMapper;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;
    private final UserServiceClient userServiceClient;

    public TransactionService(TransactionRepository transactionRepository,
                               CategoryRepository categoryRepository,
                               TransactionMapper transactionMapper,
                               UserServiceClient userServiceClient) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionMapper = transactionMapper;
        this.userServiceClient = userServiceClient;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        verifyUserExists(request.userId());

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

    /**
     * Milestone 10 — the platform's first real synchronous inter-service
     * call: before a transaction can be recorded against a userId, that user
     * must actually exist in user-service. Feign's default error decoder
     * throws {@link FeignException.NotFound} for any 404 response, which is
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
