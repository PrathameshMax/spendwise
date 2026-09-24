package com.spendwise.userservice.api;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String preferredCurrency,
        Instant createdAt,
        Instant updatedAt) {
}
