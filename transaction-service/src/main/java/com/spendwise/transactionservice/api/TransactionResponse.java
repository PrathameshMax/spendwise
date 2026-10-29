package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.domain.TransactionStatus;
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
        Instant createdAt,
        String currency,
        BigDecimal baseCurrencyAmount,
        TransactionStatus status,
        UUID reversesTransactionId,
        String reversalReason,
        MerchantDetails merchant) {

    /**
     * Same entry with merchant display details from MerchantDirectory; null
     * when the entry has no merchant or the partner had no details.
     */
    public TransactionResponse withMerchant(MerchantDetails merchant) {
        return new TransactionResponse(id, userId, categoryId, categoryName, amount, type, description,
                transactionDate, createdAt, currency, baseCurrencyAmount, status, reversesTransactionId,
                reversalReason, merchant);
    }
}
