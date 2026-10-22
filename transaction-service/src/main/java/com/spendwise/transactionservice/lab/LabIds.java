package com.spendwise.transactionservice.lab;

import java.util.UUID;

/**
 * Production Debugging Lab — identity scheme for lab-generated data.
 *
 * <p>Every lab user and every lab-created category gets a UUID whose first
 * eight hex digits are {@code 1ab5eed0} ("lab seed"). PostgreSQL orders
 * {@code uuid} values byte by byte, so all lab rows fall inside one contiguous
 * range, {@link #RANGE_START}..{@link #RANGE_END}. That lets the lab count and
 * delete its own data with an index range scan on {@code transactions.user_id}
 * without a marker column in the production schema, and without ever touching
 * rows created through the real APIs (those carry random v4 UUIDs).
 */
public final class LabIds {

    static final long PREFIX = 0x1AB5EED0_0000_0000L;
    private static final long CATEGORY_BLOCK = 0x0000_0000_FFFF_0000L;
    private static final long LOW_32_BITS = 0x0000_0000_FFFF_FFFFL;

    public static final UUID RANGE_START = new UUID(PREFIX, 0L);
    public static final UUID RANGE_END = new UUID(PREFIX | LOW_32_BITS, -1L);

    /** User indexes are positive ints, so user ids always sort below the category block. */
    public static final int MAX_USERS = Integer.MAX_VALUE;

    private LabIds() {
    }

    public static UUID userId(int userIndex) {
        if (userIndex < 1) {
            throw new IllegalArgumentException("lab user index out of range: " + userIndex);
        }
        return new UUID(PREFIX | userIndex, 0L);
    }

    /** Inverse of {@link #userId(int)}. */
    public static int userIndex(UUID labUserId) {
        if (!isLabId(labUserId) || labUserId.getLeastSignificantBits() != 0L) {
            throw new IllegalArgumentException("not a lab user id: " + labUserId);
        }
        long index = labUserId.getMostSignificantBits() & LOW_32_BITS;
        if (index < 1 || index > MAX_USERS) {
            throw new IllegalArgumentException("not a lab user id: " + labUserId);
        }
        return (int) index;
    }

    public static UUID categoryId(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex > 0xFFFF) {
            throw new IllegalArgumentException("lab category index out of range: " + categoryIndex);
        }
        return new UUID(PREFIX | CATEGORY_BLOCK | categoryIndex, 0L);
    }

    public static boolean isLabId(UUID id) {
        return id != null && (id.getMostSignificantBits() >>> 32) == (PREFIX >>> 32);
    }
}
