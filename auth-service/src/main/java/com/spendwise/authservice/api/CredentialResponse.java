package com.spendwise.authservice.api;

import java.time.Instant;
import java.util.UUID;

public record CredentialResponse(UUID id, String email, Instant createdAt) {
}
