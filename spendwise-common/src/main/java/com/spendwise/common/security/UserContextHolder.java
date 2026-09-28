package com.spendwise.common.security;

/**
 * Thread-local home for the {@link UserContext} resolved by {@link UserContextFilter}
 * for the current request. Mirrors the MDC-based correlation-ID pattern from
 * Milestone 3, but carries a typed object instead of a single string.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        CURRENT.set(context);
    }

    public static UserContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
