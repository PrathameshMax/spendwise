package com.spendwise.transactionservice.lab;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain JDBC access for the lab: bulk inserts and range deletes bypass JPA,
 * the outbox and Kafka on purpose. Seeded rows are data at rest, the way a
 * masked production copy is restored into a UAT database, not business events
 * replayed through the system.
 */
@Repository
@Profile("lab")
public class LabSeedRepository {

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions (id, user_id, category_id, amount, type, description,
                                      transaction_date, created_at, currency, base_currency_amount, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'INR', NULL, 'POSTED')
            """;

    private static final String DELETE_LAB_USER_RANGE = """
            DELETE FROM transactions
            WHERE user_id BETWEEN ? AND ?
            """;

    // PostgreSQL has no max(uuid); ORDER BY ... DESC LIMIT 1 walks the user_id index backwards instead.
    private static final String MAX_LAB_USER = """
            SELECT user_id FROM transactions
            WHERE user_id BETWEEN ? AND ?
            ORDER BY user_id DESC
            LIMIT 1
            """;

    private static final String DELETE_UNUSED_LAB_CATEGORIES = """
            DELETE FROM categories c
            WHERE c.id BETWEEN ? AND ?
              AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.category_id = c.id)
            """;

    private static final String LAB_SUMMARY = """
            SELECT count(*) AS rows, count(DISTINCT user_id) AS users,
                   min(transaction_date) AS first_date, max(transaction_date) AS last_date
            FROM transactions
            WHERE user_id BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public LabSeedRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Resolves a category id for every {@link LabCategory}: an existing
     * category with the same name is reused, a missing one is created with a
     * lab-range id.
     */
    public Map<LabCategory, UUID> ensureCategories() {
        Map<LabCategory, UUID> ids = new EnumMap<>(LabCategory.class);
        transactionTemplate.executeWithoutResult(status -> {
            for (LabCategory category : LabCategory.values()) {
                List<UUID> existing = jdbcTemplate.queryForList(
                        "SELECT id FROM categories WHERE name = ?", UUID.class, category.displayName());
                if (existing.isEmpty()) {
                    jdbcTemplate.update(
                            "INSERT INTO categories (id, name, system_default) VALUES (?, ?, false)",
                            category.labId(), category.displayName());
                    ids.put(category, category.labId());
                } else {
                    ids.put(category, existing.get(0));
                }
            }
        });
        return ids;
    }

    public void insertBatch(List<LabTransactionRow> rows, Map<LabCategory, UUID> categoryIds) {
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.batchUpdate(INSERT_TRANSACTION, rows, rows.size(),
                        (PreparedStatement ps, LabTransactionRow row) -> {
                            ps.setObject(1, row.id());
                            ps.setObject(2, row.userId());
                            ps.setObject(3, categoryIds.get(row.category()));
                            ps.setBigDecimal(4, row.amount());
                            ps.setString(5, row.type().name());
                            ps.setString(6, row.description());
                            ps.setObject(7, row.transactionDate(), Types.DATE);
                            ps.setTimestamp(8, Timestamp.valueOf(row.createdAt()));
                        }));
    }

    /** Highest lab user index that still owns transactions, or 0 when none do. */
    public int maxLabUserIndex() {
        List<UUID> max = jdbcTemplate.queryForList(MAX_LAB_USER, UUID.class, LabIds.RANGE_START, LabIds.RANGE_END);
        return max.isEmpty() ? 0 : LabIds.userIndex(max.get(0));
    }

    /**
     * Deletes the transactions of lab users {@code fromIndex..toIndex}
     * (inclusive). Deleting by user block keeps each statement on the
     * user_id index and its row count bounded, instead of one huge delete.
     */
    public int deleteLabUsers(int fromIndex, int toIndex) {
        return jdbcTemplate.update(DELETE_LAB_USER_RANGE, LabIds.userId(fromIndex), LabIds.userId(toIndex));
    }

    public int deleteUnusedLabCategories() {
        return jdbcTemplate.update(DELETE_UNUSED_LAB_CATEGORIES, LabIds.RANGE_START, LabIds.RANGE_END);
    }

    /** Refreshes planner statistics so query plans reflect the new volume immediately. */
    public void analyzeTransactions() {
        jdbcTemplate.execute("ANALYZE transactions");
    }

    public LabDataSummary summary() {
        LabDataSummary lab = jdbcTemplate.queryForObject(LAB_SUMMARY, (rs, rowNum) -> new LabDataSummary(
                rs.getLong("users"),
                rs.getLong("rows"),
                0L,
                0L,
                rs.getObject("first_date", LocalDate.class),
                rs.getObject("last_date", LocalDate.class),
                null), LabIds.RANGE_START, LabIds.RANGE_END);
        Long labCategories = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE id BETWEEN ? AND ?", Long.class,
                LabIds.RANGE_START, LabIds.RANGE_END);
        Long allRows = jdbcTemplate.queryForObject("SELECT count(*) FROM transactions", Long.class);
        String tableSize = jdbcTemplate.queryForObject(
                "SELECT pg_size_pretty(pg_total_relation_size('transactions'))", String.class);
        return new LabDataSummary(
                lab.labUsers(),
                lab.labTransactions(),
                labCategories == null ? 0L : labCategories,
                allRows == null ? 0L : allRows,
                lab.labFirstDate(),
                lab.labLastDate(),
                tableSize);
    }

    public long countLabTransactions() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE user_id BETWEEN ? AND ?", Long.class,
                LabIds.RANGE_START, LabIds.RANGE_END);
        return count == null ? 0L : count;
    }
}
