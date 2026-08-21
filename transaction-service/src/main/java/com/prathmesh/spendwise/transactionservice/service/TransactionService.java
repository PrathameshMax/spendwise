package com.prathmesh.spendwise.transactionservice.service;

import com.prathmesh.spendwise.transactionservice.dto.request.TransactionRequest;
import com.prathmesh.spendwise.transactionservice.dto.response.TransactionResponse;
import com.prathmesh.spendwise.transactionservice.entity.Transaction;
import com.prathmesh.spendwise.transactionservice.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TransactionService {

    TransactionResponse createTransaction(
            TransactionRequest request
    );

    Page<TransactionResponse> getTransactionsByUser(
            Long userId,
            Pageable pageable
    );

    TransactionResponse getTransactionById(
            Long id
    );

    TransactionResponse updateTransaction(
            Long id,
            TransactionRequest request
    );

    void deleteTransaction(
            Long id
    );

    List<TransactionResponse> getTransactionsByUserAndType(
            Long userId,
            TransactionType type
    );

    Page<TransactionResponse> getTransactions(
            Pageable pageable
    );

    List<TransactionResponse> getTransactionsByType(
            TransactionType type
    );
}
