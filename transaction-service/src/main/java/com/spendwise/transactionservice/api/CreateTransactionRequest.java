package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        UUID userId,
        UUID categoryId,
        BigDecimal amount,
        TransactionType type,
        String description,
        LocalDate transactionDate) {
}
