package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID userId,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        TransactionType type,
        String description,
        LocalDate transactionDate,
        Instant createdAt) {
}
