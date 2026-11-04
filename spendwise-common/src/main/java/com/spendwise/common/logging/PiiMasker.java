package com.spendwise.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal and secret data before it reaches a log line or an API response.
 *
 * <ul>
 *   <li>Email → first character kept: {@code a***@spendwise.dev}</li>
 *   <li>13–19 digit runs (card / account numbers) → last four kept: {@code ****1234}.
 *       UUID groups are at most 12 hex characters, so ids stay readable.</li>
 *   <li>{@code Bearer <token>} and bare JWTs ({@code eyJ...}) → redacted.</li>
 * </ul>
 */
public final class PiiMasker {

    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{9,15}(\\d{4})\\b");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = BEARER_TOKEN.matcher(text).replaceAll("$1[REDACTED]");
        masked = JWT.matcher(masked).replaceAll("[REDACTED_JWT]");
        masked = EMAIL.matcher(masked).replaceAll("$1***$2");
        return ACCOUNT_NUMBER.matcher(masked).replaceAll("****$1");
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        Matcher matcher = EMAIL.matcher(email);
        return matcher.matches() ? matcher.replaceAll("$1***$2") : "***";
    }
}
