package com.prathmesh.spendwise.transactionservice.service.impl;

import com.prathmesh.spendwise.transactionservice.client.UserServiceClient;
import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.exception.TransactionNotFoundException;
import com.prathmesh.spendwise.transactionservice.repository.TransactionRepository;
import com.prathmesh.spendwise.transactionservice.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);


    private final TransactionRepository transactionRepository;
    private final UserServiceClient userServiceClient;
    private final MeterRegistry meterRegistry;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository, UserServiceClient userServiceClient, MeterRegistry meterRegistry) {

        this.transactionRepository = transactionRepository;
        this.userServiceClient = userServiceClient;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public TransactionResponse createTransaction(
            TransactionRequest request) {

        log.info("Creating Transaction for User id = {}", request.getUserId());

        userServiceClient.getUserById(request.getUserId());

        Transaction transaction = new Transaction();

        mapRequestToEntity(request, transaction);

        Transaction saved =
                transactionRepository.save(transaction);
        meterRegistry.counter("spendwise.transactions.created").increment();
        log.info("Transaction created successfully, transactionId={}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByUser(
            Long userId,
            Pageable pageable) {

        return transactionRepository
                .findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {

        log.info("Fetching transaction, transactionId={}", id);

        Transaction transaction =
                transactionRepository.findById(id)
                        .orElseThrow(() -> {
                                log.warn("Transaction not found, transactionId={}", id);

                               return new TransactionNotFoundException(
                                        "Transaction not found with Id : " + id
                                );
                        });

        return mapToResponse(transaction);
    }

    @Override
    public TransactionResponse updateTransaction(
            Long id,
            TransactionRequest request) {
        log.info("Updating transaction, transactionId={}", id);

        Transaction transaction =
                transactionRepository.findById(id)
                        .orElseThrow(() ->
                                new TransactionNotFoundException(
                                        "Transaction not found with Id : " + id
                                )
                        );

        mapRequestToEntity(request, transaction);

        Transaction updated =
                transactionRepository.save(transaction);
        meterRegistry.counter("spendwise.transactions.updated").increment();
        log.info("Successfully updated transaction, transactionId={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    public void deleteTransaction(Long id) {

        log.info("Deleting transaction, transactionId={}", id);

        if (!transactionRepository.existsById(id)) {
            log.warn("Cannot delete transaction because it was not found, transactionId={}", id);
            throw new TransactionNotFoundException(
                    "Transaction not found with Id : " + id
            );
        }
        log.info("Transaction deleted successfully, transactionId={}", id);
        transactionRepository.deleteById(id);
        meterRegistry.counter("spendwise.transactions.deleted").increment();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByUserAndType(
            Long userId,
            TransactionType type) {

        return transactionRepository
                .findByUserIdAndType(userId, type)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public Page<TransactionResponse> getTransactions(Pageable pageable) {

        return transactionRepository
                .findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public List<TransactionResponse> getTransactionsByType(
            TransactionType type) {

        return transactionRepository
                .findByType(type)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void mapRequestToEntity(
            TransactionRequest request,
            Transaction transaction) {

        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(
                request.getTransactionDate()
        );
    }

    private TransactionResponse mapToResponse(
            Transaction transaction) {

        TransactionResponse response =
                new TransactionResponse();

        response.setId(transaction.getId());
        response.setUserId(transaction.getUserId());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setCategory(transaction.getCategory());
        response.setDescription(transaction.getDescription());
        response.setTransactionDate(
                transaction.getTransactionDate()
        );
        response.setCreatedAt(transaction.getCreatedAt());
        response.setUpdatedAt(transaction.getUpdatedAt());

        return response;
    }
}
