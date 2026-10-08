package com.spendwise.transactionservice.messaging;

/**
 * Milestone 21 — what {@link TransactionReversalService} did with one
 * {@code TransactionRejectedEvent}; the {@code outcome} tag of
 * {@code spendwise.saga.reversals}.
 */
public enum ReversalOutcome {

    /** The entry was POSTED: marked REVERSED, reversing entry written, TransactionReversedEvent recorded. */
    REVERSED("reversed"),

    /** The entry was no longer POSTED (already compensated): nothing changed. */
    ALREADY_REVERSED("already_reversed"),

    /** No entry with that id for that user: nothing to compensate. */
    UNKNOWN_TRANSACTION("unknown_transaction"),

    /** This event was already processed by this consumer; discarded by the idempotency guard. */
    DUPLICATE("duplicate");

    private final String tag;

    ReversalOutcome(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
