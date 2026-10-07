package com.spendwise.notificationservice.messaging;

import com.spendwise.common.messaging.EventIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestone 19 — the tracking filter's check-and-claim, against the
 * {@code processed_events} constraint.
 *
 * <p><b>Claim, don't check.</b> A separate "SELECT, then INSERT if absent" has
 * a race: two deliveries of the same event (a redelivery to the new partition
 * owner while the old owner is still finishing it, after a rebalance) can both
 * see "absent". This is one statement instead: {@code INSERT ... ON CONFLICT
 * DO NOTHING} on the {@code (consumer, event_id)} primary key. Postgres makes
 * the second inserter wait on the first's uncommitted row; when the first
 * commits, the second gets 0 rows (duplicate), and if the first rolls back,
 * the second gets 1 (it is now the first delivery). No window exists in which
 * both proceed.
 *
 * <p><b>{@code Propagation.MANDATORY}.</b> The claim is only meaningful inside
 * the same transaction as the side effect it guards: claimed and committed
 * alone, a crash before the side effect would mark the event processed when
 * it never was — an event lost, which is worse than a duplicate. MANDATORY
 * makes calling this outside a transaction an immediate error, not a silent
 * correctness bug.
 *
 * <p>{@link JdbcTemplate} rather than a JPA repository: the decision rests on
 * the statement's update count, which a plain SQL statement returns directly.
 * It runs on the same connection, and so in the same transaction, as the
 * service's JPA writes, because {@code JpaTransactionManager} exposes its
 * connection to JDBC access code.
 */
@Component
public class ProcessedEventGuard {

    private static final String CLAIM_SQL = """
            INSERT INTO processed_events (consumer, event_id, processed_at)
            VALUES (?, ?, now())
            ON CONFLICT (consumer, event_id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return {@code true} if this is the first time {@code consumer} sees this
     * event (the caller must now perform the side effect, in this same
     * transaction); {@code false} if it was already processed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String consumer, EventIdentity event) {
        return jdbcTemplate.update(CLAIM_SQL, consumer, event.id()) == 1;
    }
}
