package com.spendwise.transactionservice.domain;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every method returns null when its filter argument is absent, and
 * {@link #combine} drops nulls before building the final query — so an
 * unfiltered GET /api/v1/transactions request produces a plain findAll.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> hasUserId(UUID userId) {
        return userId == null ? null
                : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Transaction> hasCategoryId(UUID categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        return type == null ? null
                : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> dateFrom(LocalDate from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> dateTo(LocalDate to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> amountFrom(BigDecimal min) {
        return min == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transaction> amountTo(BigDecimal max) {
        return max == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    @SafeVarargs
    public static Specification<Transaction> combine(Specification<Transaction>... specs) {
        List<Specification<Transaction>> present = new ArrayList<>();
        for (Specification<Transaction> spec : specs) {
            if (spec != null) {
                present.add(spec);
            }
        }
        return present.stream().reduce(Specification::and).orElse(null);
    }
}
