package com.spendwise.transactionservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Milestone 17 — the outbox publisher's claim query. {@code FOR UPDATE}
     * row-locks the returned batch for the rest of the caller's transaction;
     * {@code SKIP LOCKED} makes a second transaction-service replica's
     * concurrent poll skip those rows instead of blocking on them, so N
     * replicas drain disjoint batches rather than publishing the same row
     * N times. Served by V4's partial index {@code idx_outbox_events_unprocessed}
     * ({@code created_at} where {@code processed_at IS NULL}). Native SQL
     * because JPQL has no {@code SKIP LOCKED}; Postgres-specific by design,
     * matching this service's only supported database. Must be called inside
     * an active transaction — outside one, the lock is released the instant
     * the statement completes and the guarantee is gone.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE processed_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnprocessedBatch(@Param("limit") int limit);
}
