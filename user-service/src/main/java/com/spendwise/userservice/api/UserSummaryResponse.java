package com.spendwise.userservice.api;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.spendwise.common.logging.MaskedEmailSerializer;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin listing view of a profile: an operator sees that the account exists and
 * when it was created, but not other people's full email address or name
 * (PII minimisation in API responses).
 */
public record UserSummaryResponse(
        UUID id,
        @JsonSerialize(using = MaskedEmailSerializer.class) String email,
        String preferredCurrency,
        Instant createdAt) {
}
