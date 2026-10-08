package com.spendwise.transactionservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Milestone 21 — transaction-service's own copy of budget-service's
 * compensating event, per the platform's per-consumer contract convention.
 * Only what the reversal needs is read; everything else budget-service sends
 * (budget id, cap, spend, attempted amount) is ignored, so budget-service can
 * add to its event freely (a tolerant reader, Q64).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionRejectedEvent(
        UUID transactionId,
        UUID userId,
        String reason) {
}
