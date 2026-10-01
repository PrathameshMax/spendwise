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
        this.createdAt = Instant.now();
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
}
