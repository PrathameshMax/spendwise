package com.spendwise.analyticsservice.messaging;

import com.spendwise.common.messaging.EventIdentity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Milestone 19 — the reactive twin of notification-service's guard: claims
 * {@code (consumer, event_id)} in {@code processed_events} with a single
 * {@code INSERT ... ON CONFLICT DO NOTHING} and reports whether the claim was
 * new. The same race-free reasoning applies (Postgres serialises concurrent
 * inserters on the primary key; there is no check-then-insert window).
 *
 * <p>Plain {@link DatabaseClient} SQL rather than a Spring Data R2DBC
 * repository, because the answer is the statement's update count. It joins
 * whatever reactive transaction is active in the subscriber's context, which
 * {@link TransactionIngestionService} opens with a {@code TransactionalOperator}
 * — the claim must always run inside the same transaction as the work it
 * guards.
 */
@Component
public class ProcessedEventGuard {

    private static final String CLAIM_SQL = """
            INSERT INTO processed_events (consumer, event_id, processed_at)
            VALUES (:consumer, :eventId, now())
            ON CONFLICT (consumer, event_id) DO NOTHING
            """;

    private final DatabaseClient databaseClient;

    public ProcessedEventGuard(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * @return a {@code Mono} of {@code true} for a first delivery, {@code false}
     * for an event this consumer has already processed.
     */
    public Mono<Boolean> claim(String consumer, EventIdentity event) {
        return databaseClient.sql(CLAIM_SQL)
                .bind("consumer", consumer)
                .bind("eventId", event.id())
                .fetch()
                .rowsUpdated()
                .map(rows -> rows == 1L);
    }
}
