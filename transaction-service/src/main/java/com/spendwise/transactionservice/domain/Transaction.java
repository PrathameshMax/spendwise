package com.spendwise.transactionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Milestone 21 — every entry carries a {@link TransactionStatus}. A rejected
 * entry is compensated with {@link #markReversed(String)} plus a new entry
 * from {@link #reversalOf(Transaction, String)}, never a delete: the ledger
 * stays append-only and its sum stays correct (the entry and its reversal
 * cancel out), while the history of what happened and why is kept.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(length = 500)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Milestone 13 — the ISO 4217 code the entry was actually recorded in
    // (validated at the API boundary by CreateTransactionRequest), and, only
    // when that differs from the user's preferred currency, the amount
    // ExchangeRateClient converted it to. amount/currency are never
    // overwritten with the converted figure — the ledger keeps exactly what
    // the user entered, with the converted figure stored alongside it rather
    // than in place of it.
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "base_currency_amount", precision = 19, scale = 2)
    private BigDecimal baseCurrencyAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "reverses_transaction_id", updatable = false)
    private UUID reversesTransactionId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(UUID userId, Category category, BigDecimal amount, TransactionType type,
                        String description, LocalDate transactionDate, String currency,
                        BigDecimal baseCurrencyAmount) {
        this.userId = userId;
        this.category = category;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.transactionDate = transactionDate;
        this.currency = currency;
        this.baseCurrencyAmount = baseCurrencyAmount;
        this.status = TransactionStatus.POSTED;
        this.createdAt = Instant.now();
    }

    /**
     * Milestone 21 — the compensating entry for {@code original}: same user,
     * category, type, date and currency, negated amounts, status
     * {@link TransactionStatus#REVERSAL}, linked back through
     * {@code reversesTransactionId}.
     */
    public static Transaction reversalOf(Transaction original, String reason) {
        Transaction reversal = new Transaction(
                original.getUserId(),
                original.getCategory(),
                original.getAmount().negate(),
                original.getType(),
                "Reversal of " + original.getId(),
                original.getTransactionDate(),
                original.getCurrency(),
                original.getBaseCurrencyAmount() == null ? null : original.getBaseCurrencyAmount().negate());
        reversal.status = TransactionStatus.REVERSAL;
        reversal.reversesTransactionId = original.getId();
        reversal.reversalReason = truncate(reason);
        return reversal;
    }

    /**
     * Milestone 21 — flips a POSTED entry to REVERSED. Only POSTED entries can
     * be reversed; the caller checks {@link #isPosted()} first, so a second
     * rejection of the same entry is a no-op rather than an error.
     *
     * @throws IllegalStateException if this entry is not POSTED
     */
    public void markReversed(String reason) {
        if (status != TransactionStatus.POSTED) {
            throw new IllegalStateException("Transaction %s is %s, only POSTED can be reversed".formatted(id, status));
        }
        this.status = TransactionStatus.REVERSED;
        this.reversalReason = truncate(reason);
    }

    public boolean isPosted() {
        return status == TransactionStatus.POSTED;
    }

    private static String truncate(String reason) {
        return reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBaseCurrencyAmount() {
        return baseCurrencyAmount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public UUID getReversesTransactionId() {
        return reversesTransactionId;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}
