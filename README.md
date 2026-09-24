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

Next: Milestone 3 — Distributed Request Tracing (MDC + Micrometer + OpenTelemetry).
