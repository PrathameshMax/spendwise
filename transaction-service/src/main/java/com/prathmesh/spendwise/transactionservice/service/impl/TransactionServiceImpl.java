package com.prathmesh.spendwise.transactionservice.service.impl;

import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import com.prathmesh.spendwise.transactionservice.exception.TransactionNotFoundException;
import com.prathmesh.spendwise.transactionservice.repository.TransactionRepository;
import com.prathmesh.spendwise.transactionservice.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {


    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository) {

        this.transactionRepository = transactionRepository;
    }

    @Override
    public TransactionResponse createTransaction(
            TransactionRequest request) {

        Transaction transaction = new Transaction();

        mapRequestToEntity(request, transaction);

        Transaction saved =
                transactionRepository.save(transaction);

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

        Transaction transaction =
                transactionRepository.findById(id)
                        .orElseThrow(() ->
                                new TransactionNotFoundException(
                                        "Transaction not found with Id : " + id
                                )
                        );

        return mapToResponse(transaction);
    }

    @Override
    public TransactionResponse updateTransaction(
            Long id,
            TransactionRequest request) {

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

        return mapToResponse(updated);
    }

    @Override
    public void deleteTransaction(Long id) {

        if (!transactionRepository.existsById(id)) {
            throw new TransactionNotFoundException(
                    "Transaction not found with Id : " + id
            );
        }

        transactionRepository.deleteById(id);
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
