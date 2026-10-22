package com.spendwise.transactionservice.lab;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One generated ledger entry, shaped exactly like a row of the
 * {@code transactions} table (currency INR, status POSTED, no conversion).
 */
public record LabTransactionRow(
        UUID id,
        UUID userId,
        LabCategory category,
        BigDecimal amount,
        TransactionType type,
        String description,
        LocalDate transactionDate,
        LocalDateTime createdAt) {
}
