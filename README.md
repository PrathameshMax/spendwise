# SpendWise

Enterprise Java 21 / Spring Boot 3.4+ microservices monorepo for personal-finance
tracking — built as an interview-mastery project covering DDD service boundaries,
distributed tracing, resilience patterns, event-driven consistency (SAGA/CQRS),
and production triage.

Roadmaps: `SpendWise_Functional_Roadmap_v2` and `SpendWise_Technical_Roadmap_v2` (locked).

## Module layout

| Module | Role |
|---|---|
| `spendwise-common` | Shared DTOs, exception hierarchy, correlation-ID filter |
| `auth-service` | Identity issuance, JWT auth |
| `user-service` | User profiles and preferences |
| `transaction-service` | Ledger — income/expense, categories |
| `budget-service` | Spending caps, budget tracking |
| `notification-service` | Async alert dispatch (Kafka consumer, no REST) |
| `analytics-service` | CQRS read-side dashboard projections |

`api-gateway` and `discovery-server` are added when Milestones 6 and 10 respectively
introduce them.

## Path to Polyrepo

This is a monorepo for development convenience during the interview-prep build, not
an architectural commitment. The one rule that keeps the door open to splitting it
later: **no service depends on any other module except `spendwise-common`, and
`spendwise-common` carries zero business logic** (no JPA entities, no domain models,
no service-specific validation — only generic plumbing like `CorrelationIdFilter`
and the exception hierarchy). Every service already has its own database, own port,
own config-repo entries, and no runtime dependency on any other service.

When it's time to split into standalone repos, per service:

1. **Publish `spendwise-common` as a versioned artifact** (GitHub Packages, or a
   Nexus/Artifactory) instead of resolving it via the Maven reactor. Same for
   `spendwise-parent`'s BOM, so every service keeps its managed dependency versions
   without living in the same repo.
2. **Extract with history intact**: `git subtree split -P <service> -b <service>-only`,
   then push that branch to a new `PrathameshMax/<service>` repo.
3. **Swap the `<parent>`** in each extracted service's `pom.xml` to point at the
   published `spendwise-parent` artifact instead of a sibling path.
4. **Split CI/CD** from one monorepo pipeline into one pipeline per repo — same
   build/test steps, just scoped to a single module instead of the whole reactor.

Nothing in the current module structure, database-per-service setup, or config
layout needs to change for this — it's a build/repo reorganization, not a redesign.

## Running locally

Config Server must be up before any other service, since every service now bootstraps
its configuration from it:

```bash
mvn clean install
mvn -pl config-server spring-boot:run
# in a separate terminal, once config-server is listening on 8888:
mvn -pl user-service spring-boot:run
```

Config Server runs in `native` mode, serving property files from
`config-server/src/main/resources/config-repo/` — no external Git repo or broker required.

Zipkin (the tracing backend, Milestone 3) runs via Docker Compose:

```bash
docker compose up -d zipkin
# view traces at http://localhost:9411
```

## Status

**Milestone 1 (Phase 1) complete** — monorepo BOM, per-environment profile skeleton
(`dev` / `test` / `prod`), and the shared common module.

**Milestone 2 (Phase 1) complete** — Config Server (native profile, classpath-backed
`config-repo`). Every service now imports its configuration via
`spring.config.import=configserver:http://localhost:8888` instead of bundling
per-profile YAML locally. `/actuator/refresh` is exposed on every service for
single-instance config refresh; the Spring Cloud Bus broadcast (fleet-wide refresh)
is deferred until Milestone 13, where it rides on the same Kafka cluster instead of
standing up a separate broker just for this.

**Milestone 3 (Phase 1) complete** — Distributed Request Tracing. `auth-service`,
`user-service`, `transaction-service`, `budget-service`, and `analytics-service` each
register `spendwise-common`'s `CorrelationIdFilter`, seeding/propagating
`X-Correlation-ID` into the MDC on every request. Micrometer Tracing's OpenTelemetry
bridge exports spans to Zipkin (`management.zipkin.tracing.endpoint`, centralized in
`config-repo/application.yml`), and the shared log pattern now prints
`correlationId`, `traceId`, and `spanId` together. `notification-service` is skipped
here — it has no request entry point until its Kafka listener exists at Milestone 13,
and Feign/WebClient header propagation is deferred to Milestone 10, when there's an
actual inter-service call to propagate across.

Next: Milestone 4 — Database Isolation & Dialect Testing (Flyway + Testcontainers).
