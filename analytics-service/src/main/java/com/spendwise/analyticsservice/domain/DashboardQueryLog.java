package com.spendwise.analyticsservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Milestone 15 — a genuine, queryable audit trail of every dashboard lookup
 * this service serves (userId, the period asked for, how many budget items
 * came back, and when), written via Spring Data R2DBC rather than JPA/
 * Hibernate: this is the service's one piece of real, non-blocking
 * persistence, proving the WebFlux rebuild actually completes a round trip
 * to Postgres over the reactive driver rather than merely compiling against
 * one.
 *
 * <p>{@code id} is left {@code null} by {@link #newEntry}, not assigned via
 * Hibernate-style {@code GenerationType.UUID} the way budget-service's own
 * {@code Budget} entity is (Milestone 8): Spring Data R2DBC's default
 * new-vs-existing detection for a reference-typed {@code @Id} is "null means
 * new", so the database's own {@code DEFAULT gen_random_uuid()} (this
 * migration's own {@code dashboard_query_log} table) generates the value,
 * and {@code r2dbc-postgresql}'s generated-value support returns it on the
 * same {@code INSERT} — matching the null-id-at-construction convention Spring
 * Data R2DBC expects, rather than JPA's own, different convention of
 * application-side UUID generation.
 *
 * <p>The single {@link PersistenceCreator}-annotated constructor takes every
 * persistent field (including {@code id}) so Spring Data's object-mapping
 * machinery can use it unambiguously for both reads (rehydrating a row,
 * {@code id} populated) and writes (via {@link #newEntry}, {@code id} null) —
 * resolvable thanks to this platform's parent pom.xml already compiling with
 * {@code -parameters} (maven-compiler-plugin's {@code <parameters>true</parameters>}),
 * which lets Spring Data match constructor parameter names to column names
 * without needing Lombok or {@code @ConstructorProperties}.
 */
@Table("dashboard_query_log")
public class DashboardQueryLog {

    @Id
    private final UUID id;

    @Column("user_id")
    private final UUID userId;

    @Column("period_month")
    private final String periodMonth;

    @Column("item_count")
    private final int itemCount;

    @Column("queried_at")
    private final Instant queriedAt;

    @PersistenceCreator
    public DashboardQueryLog(UUID id, UUID userId, String periodMonth, int itemCount, Instant queriedAt) {
        this.id = id;
        this.userId = userId;
        this.periodMonth = periodMonth;
        this.itemCount = itemCount;
        this.queriedAt = queriedAt;
    }

    public static DashboardQueryLog newEntry(UUID userId, YearMonth periodMonth, int itemCount) {
        return new DashboardQueryLog(null, userId, periodMonth.toString(), itemCount, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPeriodMonth() {
        return periodMonth;
    }

    public int getItemCount() {
        return itemCount;
    }

    public Instant getQueriedAt() {
        return queriedAt;
    }
}
