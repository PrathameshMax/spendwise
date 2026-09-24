package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionFilter(
        UUID userId,
        UUID categoryId,
        TransactionType type,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal minAmount,
        BigDecimal maxAmount) {
}
