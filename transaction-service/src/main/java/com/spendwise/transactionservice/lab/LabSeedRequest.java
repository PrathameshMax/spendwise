package com.spendwise.transactionservice.lab;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body of {@code POST /lab/seed}. Every field is optional:
 * users defaults to 1000, months to 12, randomSeed to 42 (same seed, same data).
 * The product users x months is capped at {@link #MAX_USER_MONTHS}
 * (roughly 2.3 million rows, ~500 MB in PostgreSQL) to protect a 16 GB laptop.
 */
public record LabSeedRequest(
        @Min(1) @Max(20_000) Integer users,
        @Min(1) @Max(36) Integer months,
        Long randomSeed) {

    public static final int DEFAULT_USERS = 1_000;
    public static final int DEFAULT_MONTHS = 12;
    public static final long DEFAULT_SEED = 42L;
    public static final int MAX_USER_MONTHS = 60_000;

    public int usersOrDefault() {
        return users == null ? DEFAULT_USERS : users;
    }

    public int monthsOrDefault() {
        return months == null ? DEFAULT_MONTHS : months;
    }

    public long seedOrDefault() {
        return randomSeed == null ? DEFAULT_SEED : randomSeed;
    }
}
