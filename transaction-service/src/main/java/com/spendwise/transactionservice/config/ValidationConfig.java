package com.spendwise.transactionservice.config;

import jakarta.validation.ClockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Milestone 13 fix (caught validating Milestone 12) — {@code CreateTransactionRequest
 * .transactionDate}'s {@code @PastOrPresent} compares against Bean Validation's
 * default {@link ClockProvider}, which resolves "now" via
 * {@code Clock.systemDefaultZone()}. Every service in this platform runs with
 * the container/JVM default timezone, effectively UTC — so "now" for this
 * validator is always UTC's current calendar date, not the submitting user's.
 * {@code LocalDate} comparison is calendar-date-only (no time-of-day, no
 * offset), so an Indian user (UTC+5:30) recording a transaction dated "today"
 * is submitting a date that is, for roughly the first 5.5 hours of their day
 * (00:00-05:29 IST), still "tomorrow" by UTC's clock — a perfectly legitimate
 * request rejected with a 400 for no reason the user caused.
 *
 * <p>The fix is deliberately <i>not</i> a bespoke validator, nor a request to
 * thread the submitting user's timezone through the API (nothing in either
 * roadmap asks for per-user timezone-aware ledger semantics, and the servers
 * themselves must stay on UTC — see the Javadoc moved here from the removed
 * inline comment). Bean Validation's {@link ClockProvider} is exactly the
 * extension point built for globally adjusting what every {@code @Past}/
 * {@code @Future}/{@code @PastOrPresent}/{@code @FutureOrPresent} constraint
 * in this service considers "now," so {@code @PastOrPresent} on
 * {@code transactionDate} needs no change at all — only this one bean.
 *
 * <p>The offset is +14 hours, not the +5:30 that would fix India specifically:
 * UTC+14 (Kiribati's Line Islands) is the furthest-ahead timezone that exists
 * anywhere on Earth, so shifting the validator's own "now" forward by exactly
 * that much guarantees every timezone's legitimate "today" is always
 * {@code <=} the validator's "now" — this is a platform-wide correctness fix
 * for any user in any timezone, not an India-specific patch, while a date that
 * is genuinely, unambiguously in the future (next week, next month) is still
 * correctly rejected.
 */
@Configuration
public class ValidationConfig {

    private static final Duration FURTHEST_AHEAD_TIMEZONE_OFFSET = Duration.ofHours(14);

    @Bean
    public ClockProvider timezoneTolerantClockProvider() {
        Clock tolerantClock = Clock.offset(Clock.systemUTC(), FURTHEST_AHEAD_TIMEZONE_OFFSET);
        return () -> tolerantClock;
    }
}
