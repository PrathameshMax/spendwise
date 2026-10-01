package com.spendwise.common.security;

public final class UserContextConstants {

    public static final String HEADER_NAME = "X-User-Context";

    /**
     * Milestone 15 — the Reactor {@code Context} key {@link ReactiveUserContextFilter}
     * publishes the verified {@link UserContext} under, read back via
     * {@code Mono.deferContextual(ctx -> ctx.getOrDefault(REACTOR_CONTEXT_KEY, null))}.
     * Deliberately not the same storage as {@link UserContextHolder}'s {@link ThreadLocal}
     * — see {@link ReactiveUserContextFilter}'s Javadoc for why a WebFlux service cannot
     * safely reuse that holder.
     */
    public static final String REACTOR_CONTEXT_KEY = "userContext";

    private UserContextConstants() {
    }
}
