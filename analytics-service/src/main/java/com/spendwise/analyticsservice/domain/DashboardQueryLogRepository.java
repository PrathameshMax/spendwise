package com.spendwise.analyticsservice.domain;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/**
 * Milestone 15 — {@link org.springframework.data.repository.CrudRepository}'s
 * reactive counterpart: every method returns {@code Mono}/{@code Flux} rather
 * than blocking, backed by Spring Data R2DBC's query-derivation machinery the
 * same way every JPA-based service's own {@code *Repository} interfaces are
 * backed by Spring Data JPA's.
 */
public interface DashboardQueryLogRepository extends ReactiveCrudRepository<DashboardQueryLog, UUID> {
}
