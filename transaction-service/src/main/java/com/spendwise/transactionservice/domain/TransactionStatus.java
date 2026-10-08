package com.spendwise.transactionservice.domain;

/**
 * Milestone 21 — a ledger entry's lifecycle (V6 migration). The ledger is
 * append-only: compensation adds an entry instead of deleting or editing one.
 * <ul>
 *   <li>{@link #POSTED} — an ordinary entry, as every transaction starts.</li>
 *   <li>{@link #REVERSED} — a POSTED entry a downstream business rule
 *   rejected; its amount stays as recorded.</li>
 *   <li>{@link #REVERSAL} — the compensating entry: the negated amount of the
 *   entry it {@code reverses}. The two sum to zero.</li>
 * </ul>
 */
public enum TransactionStatus {
    POSTED,
    REVERSED,
    REVERSAL
}
