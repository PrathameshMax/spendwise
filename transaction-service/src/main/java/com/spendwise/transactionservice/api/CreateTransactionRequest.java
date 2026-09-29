package com.spendwise.transactionservice.api;

import com.spendwise.transactionservice.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code description} stays un-annotated for presence (a transaction memo is
 * optional) but bounded in length; every other field is mandatory, matching
 * what {@code TransactionService.create} already assumes with no null-checks
 * of its own. {@code @PastOrPresent} on {@code transactionDate} rejects a
 * future-dated transaction — a defensible domain rule the existing model
 * already implies (a ledger entry records something that happened) rather
 * than a new business rule invented for this milestone.
 */
public record CreateTransactionRequest(
        @NotNull UUID userId,
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @Size(max = 500) String description,
        @NotNull @PastOrPresent LocalDate transactionDate) {
}
