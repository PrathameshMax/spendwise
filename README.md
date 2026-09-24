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

`api-gateway`, `discovery-server`, and `config-server` are added when Milestones 6, 10, and 2
respectively introduce them.

## Build

```bash
mvn clean install
```

## Status

**Milestone 1 (Phase 1) complete** — monorepo BOM, per-environment profile skeleton
(`dev` / `test` / `prod`), and the shared common module.

Next: Milestone 2 — Centralized External Configuration (Config Server).
