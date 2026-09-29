# SpendWise

Enterprise Java 21 / Spring Boot 3.4+ microservices monorepo for personal-finance
tracking — built as an interview-mastery project covering DDD service boundaries,
distributed tracing, resilience patterns, event-driven consistency (SAGA/CQRS),
and production triage.

Roadmaps: `SpendWise_Functional_Roadmap_v2` and `SpendWise_Technical_Roadmap_v2` (locked).

## Module layout

| Module | Role |
|---|---|
| `spendwise-common` | Shared DTOs, exception hierarchy, correlation-ID filter, signed user-context filter |
| `api-gateway` | Edge entry point — OAuth2 resource server, JWT validation, refresh-cookie rotation |
| `auth-service` | Identity issuance — OAuth2-style Authorization Server, JWT minting |
| `user-service` | User profiles and preferences |
| `transaction-service` | Ledger — income/expense, categories |
| `budget-service` | Spending caps, budget tracking |
| `notification-service` | Async alert dispatch (Kafka consumer, no REST) |
| `analytics-service` | CQRS read-side dashboard projections |

`discovery-server` is added when Milestone 10 introduces it.

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
mvn -pl auth-service spring-boot:run
# api-gateway needs auth-service's /oauth2/jwks reachable before it can validate
# any JWT, so start auth-service first; then, in further terminals:
mvn -pl api-gateway spring-boot:run
mvn -pl user-service spring-boot:run
```

As of Milestone 6, `api-gateway` (port 8080) is the intended entry point for every
authenticated call — `register`/`login`/`refresh` and the `user`/`transaction`/`budget`
routes should be called through it, not directly against each service's own port.
Each service's own port is still open directly (no service mesh/network policy
exists yet to prevent it — see the Milestone 6 status note below), which remains
useful for hitting Actuator/metrics endpoints per service, exactly as the Postman
collection already does.

Config Server runs in `native` mode, serving property files from
`config-server/src/main/resources/config-repo/` — no external Git repo or broker required.

Zipkin (Milestone 3), PostgreSQL (Milestone 4), and Prometheus + Grafana
(Milestone 5) all run via Docker Compose:

```bash
docker compose up -d zipkin postgres prometheus grafana
# traces:      http://localhost:9411
# metrics:     http://localhost:9090 (Prometheus)
# dashboards:  http://localhost:3000 (Grafana, admin / spendwise)
# postgres exposes 4 isolated databases: auth_db, user_db, transaction_db, budget_db
```

A Postman collection covering every live endpoint (business + Actuator + Metrics,
one folder per service) lives at `postman/SpendWise.postman_collection.json` —
import it directly.

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

**Milestone 4 (Phase 1) complete** — Database Isolation & Dialect Testing, plus basic
CRUD for `auth-service`, `user-service`, `transaction-service`, and `budget-service`
(by explicit request — the roadmap text scopes this milestone to the DB layer only,
but a bare repository with no consumer was judged not worth shipping alone). Each
service owns a real PostgreSQL database (`auth_db`, `user_db`, `transaction_db`,
`budget_db`), versioned by Flyway migrations, validated (not auto-generated) by
Hibernate (`ddl-auto: validate`), and covered by a Testcontainers repository
integration test. Highlights: `UserController`'s list endpoint is paginated and
sortable (`?page=&size=&sort=`); `TransactionController` supports dynamic filtering
by user/category/type/date-range/amount-range via JPA Specifications; `Transaction`'s
`Category` association is deliberately lazy-loaded, setting up the N+1 chaos lab at
Milestone 27; `Budget.currentSpend` starts at zero and stays there until the
Kafka-driven consumer lands at Milestone 18+. No global exception handler exists yet
(Milestone 7), so `DuplicateResourceException`/`ResourceNotFoundException` currently
surface as generic 500s — that's expected at this stage, not a bug.
`notification_db` and `analytics_db` are deferred (needed at Milestone 19 and rebuilt
on R2DBC/Mongo at Milestone 15, respectively).

**Milestone 5 (Phase 1) complete** — Deep Observability Probes & Telemetry
Dashboards. `management.endpoints.web.exposure.include` now adds `metrics` and
`prometheus` (`micrometer-registry-prometheus` added to every service). Liveness
health now includes a genuine `DeadlockHealthIndicator` (spendwise-common,
`ThreadMXBean.findDeadlockedThreads()` — the same mechanism a manual `jstack`
diagnosis would use, exposed continuously) alongside the default `livenessState`;
readiness on the 4 DB-backed services now includes the `db` indicator, so a dead
Postgres connection actually fails readiness, not just liveness. Prometheus scrapes
all 7 services' `/actuator/prometheus` (via `host.docker.internal`, since our
services aren't containerized until Milestone 9) and evaluates one alerting rule,
`HighHttp5xxErrorRate` (>5% 5xx rate over a 5-minute window, sustained 2+ minutes) —
wiring an actual notification channel (Alertmanager → Slack/email) isn't in this
milestone's scope, only defining the rule itself. Grafana auto-provisions the
Prometheus datasource and one starter dashboard (request rate, 5xx error rate, JVM
heap — all per service).

**Milestone 5 hotfix** — the `liveness` health group (`livenessState,deadlock`) is
defined once, globally, in `config-repo/application.yml`, so it applies to every
service registered with the Config Server — including `notification-service`, which
has no web-facing endpoints yet. `deadlockHealthIndicator` had only been registered
in `HealthConfig` for auth/user/transaction/budget/analytics-service, not
`notification-service`. Spring Boot's default
`management.endpoint.health.validate-group-membership=true` fails application
startup — not just the health endpoint — when a named group member has no matching
registered contributor, which is exactly what happened:
`Included health contributor 'deadlock' in group 'liveness' does not exist`.
Fixed by adding the same `HealthConfig` bean registration to `notification-service`
that the other five services already have; deadlock detection is JVM-level, not
HTTP-level, so the service needing no web layer yet doesn't exempt it from the
group.

**Milestone 6 (Phase 2) complete** — Edge Gateway Security (Spring Security 6 +
stateless JWT Authorization Server). `auth-service` is now a self-contained
OAuth2-style Authorization Server: it generates its own RSA key pair at startup
(`JwtKeyConfig`), serves the public half at `/oauth2/jwks`, and `register`/`login`
both mint a short-lived access token (15 min) plus a refresh token (7 days). Refresh
tokens are tracked in a new `refresh_tokens` table by their `jti` claim, so
`/api/v1/auth/refresh` can detect and reject a token that was already rotated out —
a real reuse/replay signal, not just an expiry check.

The new `api-gateway` module (Spring Cloud Gateway, WebFlux/reactive — Spring Cloud
Gateway has no servlet-based variant) is the platform's first edge component. As an
OAuth2 Resource Server it validates every inbound JWT against auth-service's JWKS
endpoint before routing to `user-service`, `transaction-service`, or
`budget-service` (`notification-service` and `analytics-service` have no REST
surface yet, so they aren't routed). Two custom filter pipelines do the rest of
the Implementation spec: `RefreshCookieSupport` rewrites `register`/`login`/`refresh`
request and response bodies so the refresh token travels only as an HttpOnly,
Secure, `SameSite=Strict` cookie — auth-service itself never sees a cookie, only
the `{accessToken, refreshToken}` JSON body it always returned — and
`UserContextPropagationGlobalFilter` extracts `{userId, email}` from the validated
JWT, HMAC-signs it, and forwards it as `X-User-Context`, after first stripping any
inbound copy a caller might have forged. Business services verify that signature
via a new shared `UserContextFilter` (spendwise-common) instead of decoding the JWT
themselves — exactly the "trust the Gateway" model the milestone calls for. Access
tokens carry no role/claim list, since `Credential` has no roles concept in the
domain yet; role-based vs. claim-based authorization (Q32) stays a documented
interview topic until that concept exists. `CorrelationIdGlobalFilter` is a
WebFlux-native reimplementation of the Milestone 3 correlation-ID filter, since the
servlet-based original can't run on Gateway's Netty runtime. `api-gateway` also
picked up the Milestone 5 observability baseline (Prometheus scrape target,
`DeadlockHealthIndicator` registration — a service without it fails startup exactly
as `notification-service` did before its Milestone 5 hotfix) so the new module
isn't a monitoring blind spot. Each service still exposes its own port directly for
now (no network policy prevents bypassing the Gateway) — that hardening isn't in
this milestone's stated scope.

**Milestone 7 (Phase 2) complete** — API Contract & Documentation Governance.
springdoc-openapi is wired into every service, so `/v3/api-docs` and
`/swagger-ui.html` are generated from the live controller annotations and can never
drift out of sync with the code the way a hand-maintained spec would (Q35). This
includes `notification-service` and `analytics-service`, even though neither has a
business endpoint yet — their `OpenApiConfig` beans exist now precisely so nothing
has to be retrofitted when their REST surfaces land in Milestones 19 and 15. The
API Gateway aggregates all six into one Swagger UI at its own origin
(`/swagger-ui.html`) via new per-service `/v3/api-docs/<service>` proxy routes in
`RouteConfig` — same-origin proxying, not direct cross-port fetches, so Swagger
UI's browser-side JS never hits a CORS wall; documentation endpoints stay
`permitAll()` in `SecurityConfig` while every business endpoint still requires a
JWT.

Every ad-hoc "surfaces as a generic 500" exception path called out in every prior
milestone's status notes (Milestones 1, 4, 6) is now closed: `SpendWiseException`
carries an `HttpStatus` per exception type (`ResourceNotFoundException` → 404,
`DuplicateResourceException` → 409, `BusinessValidationException` → 422,
`InvalidCredentialsException`/`InvalidRefreshTokenException` → 401), and a single
`AbstractGlobalExceptionHandler` (spendwise-common) turns any of them — plus an
unhandled catch-all — into an RFC 7807 `ProblemDetail` carrying a machine-readable
`errorCode` and the request's `correlationId` (Q34). Each service activates it via
its own two-line `@RestControllerAdvice` subclass, since Spring Boot's component
scan is per-service and won't discover a class living in `spendwise-common`'s
package on its own. `api-gateway` gets the same RFC 7807 shape a different way —
`spring.webflux.problemdetails.enabled=true` — since it has no business
`@RestController` of its own to hand a servlet-only `@RestControllerAdvice` to.

URI-based versioning (`/api/v1/...`) has been the convention since Milestone 1;
this milestone formalizes it as a documented policy rather than an implicit habit
— see **API Versioning & Deprecation Policy** below (Q33). No `/api/v2/...` exists
yet because no breaking change has forced one, consistent with not building
speculative infrastructure ahead of an actual need.

Next: Milestone 8 — Type-Safe Mapping & Validation.

## API Versioning & Deprecation Policy

- **Convention**: every endpoint is versioned in the URI (`/api/v1/...`), not via a
  header or content-negotiation scheme. It's the simplest to reason about across
  independently-deployed services, and the version is visible directly in logs,
  traces, and Gateway route definitions without inspecting request headers.
- **What forces a `v2`**: a breaking change only — removing or renaming a field or
  endpoint, changing a field's type or semantics, tightening validation in a way
  that rejects previously-valid requests, or changing what a status code means.
  An additive, backward-compatible change (a new optional field, a new endpoint, a
  new optional query parameter) ships straight into the current `v1` and never
  requires a new version.
- **Coexistence**: when a `v2` is cut, `v1` and `v2` run side by side behind the
  same Gateway and the same service — never a hard cutover that breaks whoever
  hasn't migrated yet.
- **Deprecation signaling**: once a `v2` exists, the superseded `v1` operation is
  marked `deprecated: true` in its OpenAPI annotation (renders struck-through in
  Swagger UI automatically) and its responses carry a `Deprecation: true` header
  plus a `Sunset: <date>` header (RFC 8594).
- **Sunset window**: a minimum of 90 days between a `v2` shipping and its
  corresponding `v1` operation being removed, matching common industry practice.

No endpoint in this codebase is deprecated yet — this section exists so the policy
is settled and consistent the day a breaking change first forces a `v2`, rather
than improvised under time pressure at that point.
