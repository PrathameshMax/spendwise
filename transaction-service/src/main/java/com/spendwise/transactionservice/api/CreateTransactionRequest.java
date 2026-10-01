package com.spendwise.transactionservice.api;

import com.spendwise.common.validation.ValidCurrencyCode;
import com.spendwise.transactionservice.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
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
 * than a new business rule invented for this milestone. See
 * {@code ValidationConfig} (Milestone 13 fix) for why this constraint needs a
 * custom {@code ClockProvider} to avoid rejecting a legitimate "today" from a
 * timezone ahead of the server's UTC clock.
 *
 * <p>{@code currency} (Milestone 13) is mandatory, not optional-defaulting-to-
 * the-user's-own-preference: a ledger entry should record the currency it was
 * actually transacted in (what the receipt said), not assume it always
 * matches the user's base currency — that assumption is exactly what multi-
 * currency support exists to not make. {@link TransactionService} compares it
 * against the user's {@code preferredCurrency} and only invokes
 * {@code ExchangeRateClient} when they differ.
 */
public record CreateTransactionRequest(
        @NotNull UUID userId,
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @Size(max = 500) String description,
        @NotNull @PastOrPresent LocalDate transactionDate,
        @NotBlank @ValidCurrencyCode String currency) {
}
