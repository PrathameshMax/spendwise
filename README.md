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
| `discovery-server` | Eureka service registry — every other module self-registers with it |
| `api-gateway` | Edge entry point — OAuth2 resource server, JWT validation, refresh-cookie rotation |
| `auth-service` | Identity issuance — OAuth2-style Authorization Server, JWT minting |
| `user-service` | User profiles and preferences |
| `transaction-service` | Ledger — income/expense, categories |
| `budget-service` | Spending caps, budget tracking |
| `notification-service` | Async alert dispatch (Kafka consumer, no REST) |
| `analytics-service` | CQRS read-side dashboard projections |

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

### Option A — everything via Docker Compose (Milestone 9)

The simplest way to run the whole platform: every module now has its own
multi-stage Dockerfile, and the root `docker-compose.yml` wires all of it
together — infra, health-check-gated startup order, and per-container CPU/
memory limits included.

```bash
docker compose up -d --build
# api-gateway:            http://localhost:8080
# each service also still reachable directly: 8081 (auth) .. 8086 (analytics), 8888 (config-server)
# notification-service (Milestone 18, scalable): 18085, or 18085-18088 when scaled to 4
# service registry: http://localhost:8761 (Eureka dashboard — registered instances)
# traces:      http://localhost:9411
# metrics:     http://localhost:9090 (Prometheus)
# dashboards:  http://localhost:3000 (Grafana, admin / spendwise)
# Kafka (Milestone 17): brokers on localhost:29092/39092/49092
# Kafka UI (optional):  docker compose --profile tools up -d kafka-ui  ->  http://localhost:8090
```

Compose brings services up in dependency order — `postgres`/`config-server`
first, then `auth`/`user`/`transaction`/`budget-service`, then `api-gateway`
last — because each `depends_on` entry waits on `condition: service_healthy`,
not merely on the upstream container having started (Q39/Q40: a JVM
reporting "started" and its Spring context actually being ready to serve
`/actuator/health` can be seconds apart). Rebuild after a code change with
`docker compose up -d --build <service-name>`.

### Option B — services via IDE/Maven, infra via Docker Compose

Useful for active development on a single service, where an IDE's hot-reload
beats a full image rebuild per change. Config Server must be up before any
other service, since every service bootstraps its configuration from it:

```bash
mvn clean install
mvn -pl config-server spring-boot:run
# discovery-server has no config-server dependency of its own — start it any
# time, in any order relative to config-server (Milestone 10: Eureka client
# registration is best-effort/asynchronous, not a hard boot dependency):
mvn -pl discovery-server spring-boot:run
# in a separate terminal, once config-server is listening on 8888:
mvn -pl auth-service spring-boot:run
# api-gateway needs auth-service's /oauth2/jwks reachable before it can validate
# any JWT, so start auth-service first; then, in further terminals:
mvn -pl api-gateway spring-boot:run
mvn -pl user-service spring-boot:run
```

```bash
# Milestone 13 fix — plain `docker compose up -d zipkin postgres prometheus
# grafana` starts Prometheus with prometheus.yml, whose targets are compose
# service names (api-gateway:8080, etc.) that don't exist on this network in
# Option B, since the services themselves are running on the host, not as
# containers — every target would show DOWN. Layer the IDE override on top so
# Prometheus gets host.docker.internal targets instead (see
# docker-compose.override.ide.yml and infra/prometheus/prometheus-ide.yml).
# Milestone 14 adds `redis` to this list: api-gateway's RedisRateLimiter needs
# it reachable regardless of which option starts the services themselves, and
# unlike Prometheus's scrape targets, redis needs no IDE-specific override —
# its container already publishes 6379 to the host, so api-gateway's own
# ${REDIS_HOST:localhost} default (config-repo/api-gateway.yml) reaches it
# unchanged, exactly like postgres's DB_HOST default already does.
# Milestone 17 adds postgres-db-init (creates any missing service database
# on an existing volume, then exits) and the three Kafka brokers, which
# transaction-service's outbox publisher reaches on the host-published ports
# its ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092,...} default already lists.
docker compose -f docker-compose.yml -f docker-compose.override.ide.yml \
  up -d zipkin postgres postgres-db-init redis kafka-1 kafka-2 kafka-3 prometheus grafana
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

A Postman collection covering every live endpoint (business + Actuator + Metrics,
one folder per service) lives at `postman/SpendWise.postman_collection.json` —
import it directly; every request still targets `localhost`, so it works unchanged
against either option above.

## Status

**Milestone 1 (Phase 1) complete** — monorepo BOM, per-environment profile skeleton
(`dev` / `test` / `prod`), and the shared common module.

**Milestone 2 (Phase 1) complete** — Config Server (native profile, classpath-backed
`config-repo`). Every service now imports its configuration via
`spring.config.import=configserver:http://localhost:8888` instead of bundling
per-profile YAML locally. `/actuator/refresh` is exposed on every service for
single-instance config refresh; the Spring Cloud Bus broadcast (fleet-wide refresh)
is deferred until Kafka exists on the platform (Milestone 17) rather than standing
up a separate broker just for this. (Correction: this note originally pointed to
"Milestone 13" from an earlier, pre-v2 roadmap numbering — v2's actual Milestone 13
is Resilience4j's Bulkhead/TimeLimiter, with no Kafka involved; left uncorrected
until Milestone 13 itself actually shipped and made the stale forward-reference
obvious.)

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

**Milestone 8 (Phase 2) complete** — Type-Safe Mapping & Validation.

Every hand-written `private XResponse toResponse(XEntity entity)` method across the
four business services (`AuthService`, `UserService`, `CategoryService`,
`TransactionService`, `BudgetService`) is gone, replaced by a MapStruct
`@Mapper(componentModel = "spring")` interface per entity (`CredentialMapper`,
`UserProfileMapper`, `CategoryMapper`, `TransactionMapper`, `BudgetMapper`).
MapStruct generates the mapping implementation at compile time — a field renamed
on either side of a mapping fails the build immediately instead of silently
returning `null` at runtime the way a hand-written mapper degrades. Scope is
deliberately entity-to-response-DTO only: the request side (`UserRequest` ->
`UserProfile`, etc.) stays an explicit domain-constructor call, since that is
where domain invariants belong, and a generic mapper would bypass them.
`TransactionMapper` is the one mapper with `@Mapping` directives, flattening the
nested `Transaction.category.id`/`category.name` association onto
`TransactionResponse`'s flat `categoryId`/`categoryName` fields — the textbook
case the dotted `source` path exists for.

Every request DTO across all four services now carries Jakarta Bean Validation
annotations (`@NotBlank`, `@Email`, `@Size`, `@NotNull`, `@Positive`,
`@PastOrPresent`) enforced via `@Valid` on each controller's `@RequestBody`
parameter, closing the last gap in Milestone 7's error-response contract: a
malformed request body used to reach the service layer and fail unpredictably
(a `NullPointerException`, a database constraint violation) instead of being
rejected at the edge with a clear, structured error.

`user-service`'s `CreateUserRequest`/`UpdateUserRequest` are merged into one
`UserRequest`, using Bean Validation **groups** (`OnCreate`/`OnUpdate`,
spendwise-common) to keep create-vs-update semantics precise — exactly the
redesign `UpdateUserRequest`'s own Javadoc had flagged as pending since
Milestone 2. `email`/`fullName`/`preferredCurrency` are `@NotBlank` only in the
`OnCreate` group, so a partial update can still omit them; `@Size`/
`@ValidCurrencyCode` carry no group at all, so they run under the implicit
`Default` group on *both* create and update, since `OnCreate`/`OnUpdate` both
`extends Default` (a well-known Bean Validation gotcha — a custom group does
not imply `Default` unless it says so explicitly). The controller selects the
active group with Spring's `@Validated(OnCreate.class)` /
`@Validated(OnUpdate.class)`, since plain `@Valid` cannot specify one. This
also fixes a latent bug: `UserProfile.updateProfile` only skips a `null` field,
so `fullName: ""` used to silently overwrite a valid name with an empty string;
the un-grouped `@Size(min = 1, ...)` now rejects that on both create and
update.

One custom constraint, `@ValidCurrencyCode` (spendwise-common, backed by
`CurrencyCodeValidator`), validates against the JVM's own `java.util.Currency`
ISO 4217 registry rather than a hand-maintained code list, applied to
`UserRequest.preferredCurrency` — the only currency-shaped field anywhere in
the platform. The validator is null-tolerant by convention (returns valid for
`null`), leaving presence to the group-scoped `@NotBlank` so the two compose
correctly instead of duplicating each other's job.

`AbstractGlobalExceptionHandler` (spendwise-common) now overrides
`handleMethodArgumentNotValid`, enriching the `ProblemDetail`
`ResponseEntityExceptionHandler` already builds for a failed `@Valid`/
`@Validated` request with the same `errorCode`/`correlationId` properties every
other error path already carries (`VALIDATION_FAILED`) — without this, a
validation failure would have been the one error response on the whole
platform that didn't match the Milestone 7 contract.

**Milestone 9 (Phase 2) complete** — Enterprise-Grade Containerization & Memory Quotas.

Every one of the platform's eight modules (`config-server` plus the six business
services plus `api-gateway`) now has its own multi-stage Dockerfile
(`<module>/Dockerfile`): a `maven:3.9.9-eclipse-temurin-21` build stage compiles
just that module (`mvn -pl <module> -am`, so only `spendwise-common` plus the
target module build, not the full nine-module reactor), and a slim
`eclipse-temurin:21-jre-alpine` runtime stage copies out only the resulting fat
jar — the JDK, Maven, and the entire dependency cache never reach the image
that actually ships (Q39). Every runtime image sets
`JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"`:
JDK 21 already detects a container's cgroup memory limit, but its default
`MaxRAMPercentage` (25%) leaves most of a constrained container unused by the
heap; 75% is the standard production compromise that still leaves headroom
for metaspace, thread stacks, and direct buffers — the actual cause of most
"heap graph looked fine but the container still got OOMKilled" incidents
(Q40), since the kernel's OOM killer accounts for the JVM's total RSS, not
just heap. `ExitOnOutOfMemoryError` turns a corrupted, half-alive JVM into a
clean process exit so Compose's `restart: unless-stopped` policy has
something to actually recover from.

The root `docker-compose.yml` — third-party backends only since Milestones
3-5, per that file's own forward-reference comment — now builds and wires all
eight of our own services in too: health-check-gated `depends_on` (config-server
and postgres before the business services; those before api-gateway, since
Compose's `condition: service_healthy` polls each image's `HEALTHCHECK`
instruction against `/actuator/health`, not merely "the process started") and
a `deploy.resources.limits` CPU/memory quota per service, so one runaway
container can't starve the others on a shared host. `postgres` gets an
explicit `pg_isready` healthcheck of its own, since the official image ships
none.

Containerizing services that used to all share one host's `localhost`
surfaced the one piece of cross-service wiring that assumed that: api-gateway's
routes and its JWKS URI (`config-repo/api-gateway.yml`) were hardcoded to
`localhost:<port>`, which breaks the moment api-gateway and every business
service become separate containers with separate network namespaces. Each
host is now parameterized (`http://${AUTH_SERVICE_HOST:localhost}:8081`,
etc.) using the exact same `${VAR:localhost}` convention `DB_HOST` has used
since Milestone 4 — the `localhost` default keeps IDE-based development
(Option B below) working unchanged, while docker-compose.yml overrides each
`*_SERVICE_HOST` env var to the target's own compose service name.
`infra/prometheus/prometheus.yml`'s scrape targets moved the same way — from
`host.docker.internal:<port>` to `<service-name>:<port>` — now that Prometheus
and every service it scrapes share one compose network instead of Prometheus
reaching out to the host.

**Milestone 10 (Phase 2) complete** — Dynamic Discovery & Inter-Service Communication.

A new ninth module, `discovery-server`, is a standalone Netflix Eureka registry
(`@EnableEurekaServer`, port 8761): `register-with-eureka`/`fetch-registry` are
both `false` on the registry itself, since a single standalone instance is
neither registering with nor pulling a peer list from itself — a real
multi-node cluster would flip both to `true` and point `defaultZone` at its
peers instead. Every other module now carries
`spring-cloud-starter-netflix-eureka-client` and, unlike the Config Server
client, needs no enabling annotation of its own — Spring Cloud auto-configures
registration the moment the starter is on the classpath. The registration
target itself (`eureka.client.service-url.defaultZone`) is centralized in
`config-repo/application.yml` for the seven config-repo-consuming modules,
following the same `${EUREKA_HOST:localhost}` convention `DB_HOST` and
`CONFIG_SERVER_URL` already established; `config-server` itself is a config
*producer*, not a consumer, so its own registration is set directly in its own
`application.yml` instead. `eureka.instance.prefer-ip-address: true` is set
platform-wide — without it, Eureka registers a container under its (often
unresolvable-by-siblings) container hostname rather than its routable IP,
which would silently break every Feign call below the moment services move
from bare-metal/IDE runs into Docker (Q41/Q42).

`discovery-server` is deliberately absent from every other service's
`depends_on` in `docker-compose.yml`: `config-server`'s `fail-fast: true`
makes it a hard synchronous boot dependency (a service can't even read its own
`application.yml` without it), but Eureka client registration is asynchronous
and best-effort by design — a service starts and serves traffic fine before
its first successful registration, retrying quietly in the background. Gating
container startup order on it would erase that real architectural
distinction rather than fix an actual problem, so the ~30s propagation delay
between a service registering and every client's local registry cache
refreshing is a documented, deliberate characteristic of eventually-consistent
service discovery, not a defect to engineer away.

With a registry in place, `transaction-service` and `budget-service` gained
the platform's first real synchronous inter-service call: before either can
create a transaction or a budget, it now calls `user-service`'s existing
`GET /api/v1/users/{id}` to confirm the referenced `userId` actually exists,
via a `UserServiceClient` `@FeignClient(name = "user-service")` interface
declared independently in each of the two consumers (not centralized in
`spendwise-common`, whose "zero business logic" rule excludes any
service-specific contract like this one — see "Path to Polyrepo" above). Each
consumer also declares its own minimal `UserExistenceResponse(UUID id)`
record rather than depending on `user-service`'s actual `UserResponse` DTO,
deliberately decoupling the two services' contracts; Spring Boot's default
Jackson configuration ignores the response fields this record doesn't
declare. `@EnableFeignClients` is added explicitly to
`TransactionServiceApplication`/`BudgetServiceApplication` — this one *does*
need an enabling annotation, unlike the Eureka client — and a 404 from
`user-service` (Feign's default error decoder throws
`FeignException.NotFound`) is translated back into this platform's own
`ResourceNotFoundException("UserProfile", userId)`, so the caller sees the
same RFC 7807 shape (Milestone 7) whether the check happened locally or over
the network.

Finally, correlation-ID propagation across that call — a gap planted back in
the Milestone 3 status note above ("deferred until Milestone 10, when there's
an actual inter-service call to propagate across") — is closed by a new
`spendwise-common` auto-configuration, `SpendwiseFeignTracingAutoConfiguration`,
following the same `@AutoConfiguration`/`AutoConfiguration.imports` pattern
`SpendwiseHealthAutoConfiguration` and `SpendwiseObservationAutoConfiguration`
established: `@ConditionalOnClass(RequestInterceptor.class)` means it only
activates for the two services that actually put OpenFeign on their
classpath, registering a `RequestInterceptor` that copies the current
`X-Correlation-ID` out of the SLF4J MDC onto every outgoing Feign request —
the Feign equivalent of what `CorrelationIdFilter` already does for inbound
servlet requests and `CorrelationIdGlobalFilter` does at the reactive API
Gateway.

**Milestone 11 (Phase 2) complete** — Defensive Network Isolation (Timeouts & Retries).

Milestone 10's `UserServiceClient` Feign call had no explicit timeout of its
own, meaning it inherited Feign's built-in defaults (a 10s connect timeout, a
60s read timeout) — generous enough that a slow user-service wouldn't fail
the caller's request so much as just quietly hold its thread and connection
open for up to a minute. `spring.cloud.openfeign.client.config.default.
connect-timeout`/`read-timeout` (2000ms / 1500ms) is centralized in
config-repo/application.yml under the `default` client name, so it applies to
every Feign client platform-wide, present or future, not just today's one
named "user-service" client — the same "inert on services that don't use it"
centralization already used for Eureka's `defaultZone` in Milestone 10. The
connect timeout stays generous (a TCP handshake to a sibling container on the
same compose network should be near-instant, so a slow *connect* usually means
something is actually wrong); the read timeout is deliberately tight, so a
slow response fails fast instead of parking the calling thread (Q43/Q44 — see
the Chaos Lab below for how to see the difference firsthand).

Layered on top: a Resilience4j `@Retry("userServiceLookup")` on
`UserServiceClient.getById` in both transaction-service and budget-service —
annotated on the Feign interface method itself rather than on the
`verifyUserExists` call site, since Resilience4j's annotation support is
Spring-AOP-proxy-based and only intercepts calls arriving from outside the
bean (a private method calling itself wouldn't be advised at all). The retry
policy (also centralized in config-repo/application.yml) uses exponential
backoff with randomized jitter — a fixed delay would have every concurrently-
retrying caller hammering user-service again at the exact same instant, a
self-inflicted thundering herd — and `retry-exceptions` is narrowed to
`feign.RetryableException` specifically, the type Feign wraps connect/read-
timeout and other I/O failures in. This deliberately excludes
`FeignException.NotFound`: retrying a legitimate "this user doesn't exist"
answer three times would be pure waste, and Q45's real point — retry only
what's actually transient, never a definitive business answer. `spring-boot-
starter-aop` (already present in both services' POMs since Milestone 9's fix
commits) is what lets `resilience4j-spring-boot3` wire this up with zero
`@Enable...` annotation needed anywhere.

One honest limitation, left as-is on purpose rather than over-engineered
away: Retry alone doesn't distinguish a one-off network blip from a
genuinely overloaded downstream — three retries against a user-service that's
actually struggling just adds three times the load. That's precisely the gap
Milestone 12's Circuit Breaker closes (stop retrying into a downstream that's
already failing), not something this milestone should reach ahead and solve.

🔥 **Chaos Lab — try it yourself:**
1. In `user-service`'s `UserController.getById`, temporarily add
   `Thread.sleep(5000);` as the method's first line, then rebuild/restart just
   that one container: `docker compose up -d --build user-service`.
2. Fire a handful of concurrent requests at transaction-service through the
   gateway (`for i in {1..10}; do curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" -X POST http://localhost:8080/api/v1/transactions -H "Content-Type: application/json" -d '{"userId":"<existing-id>","categoryId":"<existing-id>","amount":10,"type":"EXPENSE","description":"chaos","transactionDate":"2026-10-01"}' & done; wait`).
3. Watch the response times: each request fails in ~1.5-4.5s (one to three
   retries, each capped by the 1.5s read timeout) instead of hanging for the
   full 5s — proof the caller's threads/connections free up fast instead of
   piling up behind a slow downstream, exactly the difference Milestone 9's
   `docker stats` habit (Q39/Q40) would show as a thread-pool/connection-pool
   problem if this timeout weren't in place.
4. Revert the `Thread.sleep(5000)` line and rebuild user-service again before
   moving on.

**Milestone 12 (Phase 2) complete** — Fault Isolation State Machines (Circuit Breakers).

The roadmap scopes this one precisely to "the Transaction→User cross-service
call" — singular, not both Milestone 10 consumers — so `@CircuitBreaker` is
applied only to transaction-service's `UserServiceClient.getById`;
budget-service's identical-looking copy keeps Milestone 11's
Retry-with-timeouts protection only, unchanged. That scoping also decided
where the new config lives: Milestone 11's timeouts/retry were centralized in
config-repo/application.yml because they applied to *every* Feign client, but
the circuit breaker policy (`resilience4j.circuitbreaker.instances.
userServiceLookup`) sits in transaction-service's own application.yml instead,
since implying it's a platform-wide default would be inaccurate.

`sliding-window-size: 10` with `minimum-number-of-calls: 5` means a failure
rate is only computed once at least 5 of the last 10 calls have actually
happened — the first unlucky call can't trip the breaker off a 100%-of-1
sample. `failure-rate-threshold: 50` opens the circuit once half of that
window failed; `wait-duration-in-open-state: 10s` with
`automatic-transition-from-open-to-half-open-enabled: true` means the circuit
tries itself again on a timer (up to 3 trial calls,
`permitted-number-of-calls-in-half-open-state`) rather than waiting for the
next real caller to trigger that check.

Stacking order mattered here in a way that isn't obvious from the annotations
alone (Milestone 13 digs further into this): Resilience4j's default aspect
order makes `@Retry` the *outer* layer and `@CircuitBreaker` the *inner* one,
so each of Retry's attempts makes its own fresh pass through the circuit
breaker. A `fallbackMethod` attached to the *inner* annotation would fire on
every single attempt and silently swallow Retry's remaining attempts before
they ever ran — so `fallbackMethod = "getByIdFallback"` is attached to
`@Retry` instead, firing exactly once after the whole chain has had its say,
whether that means "all retries exhausted" or "the circuit was already OPEN
and rejected the call outright with `CallNotPermittedException`" (Q46).

`getByIdFallback` throws a new, deliberately generic
`DownstreamServiceUnavailableException` (spendwise-common — parameterized by
service name like `ResourceNotFoundException` is by resource name, so any
future circuit-breaker-protected call can reuse it), mapped to `503 Service
Unavailable` through the same `AbstractGlobalExceptionHandler` every other
`SpendWiseException` already uses — no new handler code needed.

Getting this right required one correctness detail that a first pass got
wrong (caught by actually load-testing the 404 path, not by code review —
see **Post-ship correction** below): both the retry policy and the circuit
breaker policy exclude `feign.FeignException$NotFound` via their own
`ignore-exceptions`, but that config *only* governs each policy's own core
decision — whether Retry attempts again, whether the circuit counts the call
as a failure. It does **not** stop Resilience4j-Spring's fallback-routing
wrapper from invoking `fallbackMethod`, which catches *any* exception the
decorated call produces regardless of `ignore-exceptions`. The real guard is
inside `getByIdFallback` itself: it inspects the throwable and rethrows
`FeignException.NotFound` unchanged, letting it reach
`TransactionService`'s existing `catch (FeignException.NotFound ex)` block
exactly as it would with no Resilience4j decoration at all. Only a
genuinely broken or overloaded downstream — not a legitimate "no such
user" — now reaches the fallback's `503` conversion. A business answer is
not a partial failure (Q47).

> **Post-ship correction.** The first Milestone 12 commit (`e835474`) relied
> solely on the two `ignore-exceptions` entries above to keep a 404 out of
> the fallback, which is incorrect for the reason just explained — a
> 404 was still reaching `getByIdFallback` and coming back as a false `503`.
> Running the validation steps below against a real request caught it
> immediately. The fix adds the explicit `instanceof FeignException.NotFound`
> rethrow inside `getByIdFallback`; the `ignore-exceptions` entries stay,
> since they're still correct and necessary for their own narrower purpose
> (keeping a 404 out of the retry decision and out of the circuit's
> failure-rate bookkeeping), just not sufficient on their own to protect the
> fallback.

🔥 **Chaos Lab — try it yourself:**
1. In `user-service`'s `UserController.getById`, temporarily replace the
   method body with `throw new RuntimeException("Simulated failure");` and
   rebuild: `docker compose up -d --build user-service`.
2. Fire a burst of concurrent, *authenticated* requests at transaction-service
   through the gateway — at least 5-10, to clear `minimum-number-of-calls`
   (api-gateway requires a JWT since Milestone 6; `source scripts/smoke-env.sh`
   first for `$GW`/`$AUTH`/`$USER_ID`/`$CATEGORY_ID` — a corrected version of
   an earlier copy of this exact command, which was missing `$AUTH` entirely):
   ```bash
   source scripts/smoke-env.sh
   for i in {1..15}; do
     curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/transactions \
       -H "$AUTH" -H "Content-Type: application/json" \
       -d "{\"userId\":\"$USER_ID\",\"categoryId\":\"$CATEGORY_ID\",\"amount\":5,\"type\":\"EXPENSE\",\"description\":\"chaos\",\"transactionDate\":\"2026-10-01\",\"currency\":\"INR\"}"
   done
   ```
3. Watch `docker compose logs transaction-service --since 2m | grep -i "CircuitBreaker\|fell back"` — you'll see the circuit breaker's own state-transition log line (`CLOSED` → `OPEN`) around the point `failure-rate-threshold` is crossed, followed by `userServiceLookup` logging the fallback firing.
4. Notice the later requests in the burst return `503` essentially instantly — no more waiting through 3 retry attempts each, since `CallNotPermittedException` short-circuits before any network call is even attempted. That's the thread-pool protection the roadmap calls out: once OPEN, transaction-service stops spending threads/connections on a downstream it already knows is failing.
5. Stop sending traffic, wait past `wait-duration-in-open-state` (10s), then send one more request — the circuit moves to `HALF_OPEN` and lets it through as a trial call.
6. Revert `UserController.getById` and rebuild user-service again before moving on.

**Milestone 13 (Phase 2) complete** — The Full Resilience4j Suite (Bulkhead &
TimeLimiter). Also folds in every fix noted while validating Milestone 12, per
the standing rule that a fix found during validation ships in the next
milestone rather than waiting indefinitely: Spring Cloud LoadBalancer's own
retry layer disabled (it was silently multiplying Resilience4j's `@Retry`
attempts), field-level validation errors on every 400, a `ClockProvider` fix
for `@PastOrPresent` rejecting a legitimate "today" from timezones ahead of
UTC, the API Gateway's duplicate `X-Correlation-ID` response header actually
fixed in code this time, a BuildKit Maven cache mount and non-root `USER` in
every Dockerfile, and an IDE-mode Prometheus config for Option B. Each is
documented at its own change, not repeated here.

The roadmap's own two new modules needed something real to protect, which
exposed a gap: `transaction-service` had no multi-currency logic at all, even
though both locked roadmaps already committed to it (Functional Roadmap,
Transaction Service responsibility #5, and Workflow 1 Step 2 — "converts it
via a resilient WebClient call to the external exchange-rate API"). Rather
than silently inventing scope or silently skipping the literal "exchange-rate-
API call" the roadmap names, this was raised explicitly before building
anything (see the session's own record) — building the real feature was the
chosen path, scoped tightly to what the Bulkhead/TimeLimiter exercise actually
needs rather than a fully-general currency-aware ledger: `currency` is now a
required field on every transaction, and converting it to the user's own
`preferredCurrency` (fetched from `user-service`, extending the already-
minimal `UserExistenceResponse`) populates a new, separate
`baseCurrencyAmount` column — the originally-entered amount and currency are
never overwritten, only accompanied.

Two independent resilience additions, deliberately NOT both applied to the
same call, matching exactly how the roadmap phrases them:

- **`ThreadPoolBulkhead` on the exchange-rate call** (`ExchangeRateClient`,
  new). This is the platform's first genuine third-party integration — a real
  external host with no Eureka registration, called over a blocking
  `WebClient` (this service is Spring MVC/Tomcat throughout; making one call
  reactive while `TransactionService.create` stays a synchronous
  `@Transactional` method buys nothing). `type = THREADPOOL` is what actually
  earns the roadmap's own framing ("so a slow third party can never starve
  the pool that serves internal calls"): Resilience4j submits the ENTIRE
  annotated method to a small, separately-sized pool
  (`resilience4j.bulkhead.thread-pool-instances.exchangeRateLookup`,
  transaction-service's own `application.yml` — service-local, same reasoning
  Milestone 12 already applied to its `CircuitBreaker` config), so a hanging
  exchange-rate API can only ever exhaust that small pool, never Tomcat's own.
- **`TimeLimiter` on an async, `CompletableFuture`-based wrapper around the
  user-service Feign call** (`AsyncUserServiceLookup`, new). Spring Cloud
  OpenFeign is purely synchronous by design (confirmed against
  [resilience4j/resilience4j#1111](https://github.com/resilience4j/resilience4j/issues/1111)
  and [Spring Cloud OpenFeign's own reference docs](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/),
  which explicitly defers reactive/async support to the separate
  `feign-reactive` project) — `@TimeLimiter` directly on a `@FeignClient`
  method fails at runtime with "Return type not supported." The fix is a
  second bean wrapping the existing, unmodified `UserServiceClient.getById`
  (still carrying its own `@Retry`+`@CircuitBreaker`) in
  `CompletableFuture.supplyAsync` on a dedicated, hand-sized executor (not the
  shared `ForkJoinPool` common pool — a slow lookup competing there would
  starve unrelated async work in the same JVM), with `@TimeLimiter` applied to
  that wrapper method instead.

**Decorator stacking order, verified, not assumed.** Milestone 12's Javadoc
already established that `@Retry` wraps `@CircuitBreaker` (Retry outer,
CircuitBreaker inner) from hands-on debugging. Adding two more decorators
needed the complete picture confirmed against
[Resilience4j's own documentation](https://resilience4j.readme.io/v1.5.0/docs/getting-started-3)
rather than assumed from the roadmap blurb's own prose ordering (which lists
"Bulkhead → TimeLimiter," not quite Resilience4j's actual default): the real,
documented default is

```
Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
```

— Retry outermost, Bulkhead innermost. This platform uses no `@RateLimiter`
yet (Milestone 14 introduces rate limiting, at the Gateway rather than via
this annotation), so the effective chain on `UserServiceClient.getById` itself
is `Retry → CircuitBreaker` (unchanged since Milestone 12), with `TimeLimiter`
now wrapping the whole thing one level further out, in `AsyncUserServiceLookup`
— every retry attempt, and the circuit's own accept/reject decision, happens
*inside* the time-limited window, not each individually re-timed. `Bulkhead`
on `ExchangeRateClient` is architecturally independent — a completely
separate call with no Retry or CircuitBreaker of its own — so no stacking
order question even arises there; Q49 below goes through why the order
matters when it does.

**Q48: What is a Bulkhead, and how is it different from a Circuit Breaker?**
A Bulkhead limits *concurrency* into a protected call — at most N calls (or
threads, for `ThreadPoolBulkhead`) run at once, with anything beyond that
either queued (up to a bound) or rejected outright with
`BulkheadFullException`. A Circuit Breaker tracks *failure rate* over a
sliding window of *completed* calls and, once a threshold is crossed, stops
attempting the call at all for a while (OPEN), regardless of how much spare
concurrency exists. They solve different failure modes and compose rather
than substitute for each other: a Circuit Breaker alone doesn't stop ten
concurrent slow-but-not-yet-failing calls from each holding a thread; a
Bulkhead alone doesn't stop a downstream that's failing fast (and therefore
never saturating the bulkhead) from being hammered with doomed retries. This
platform's `ThreadPoolBulkhead` specifically isolates *which thread pool* pays
for a slow call (the whole point of `type = THREADPOOL` over the lighter-
weight `SEMAPHORE` type, which limits concurrency without moving execution to
a separate pool) — a Circuit Breaker never touches thread-pool isolation at
all, only the decision to keep calling or not.

**Q49: In what order do Resilience4j decorators execute when stacked, and
why does the order matter?** Per Resilience4j's own documented default:
`Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))` — Retry
outermost, Bulkhead innermost (configurable per-aspect via
`resilience4j.<type>.<typeName>AspectOrder` properties, not by reordering the
annotations textually on the method, which has no effect on execution order).
The order matters because each layer changes what the layer *outside* it
actually measures or repeats: `fallbackMethod` must sit on the OUTERMOST
annotation actually declared, or it fires after every single inner attempt
instead of once after the whole chain gives up (Milestone 12's own fix).
Retry being outermost means each retry attempt gets its own fresh pass
through CircuitBreaker — a circuit that's already OPEN rejects every retry
attempt instantly via `CallNotPermittedException` rather than letting them
exhaust their backoff delays uselessly. TimeLimiter sitting outside Bulkhead
(and, in this platform's composed case, outside Retry+CircuitBreaker too,
via `AsyncUserServiceLookup`) means the timeout bounds the *whole* protected
operation — every retry, every circuit check — not just one raw attempt;
placed the other way around, a tight per-attempt timeout could fire before
Retry even got to try its second attempt, silently neutering Retry. Bulkhead
innermost means it is the last gate before the actual call, measuring only
genuine concurrent *in-flight* work, not retry/circuit-breaker bookkeeping
overhead.

**Q50: How does a TimeLimiter interact with an already-configured
connect/read timeout?** They bound different things and both still apply —
TimeLimiter doesn't replace or override a lower-level HTTP timeout, it adds an
outer ceiling on top of it. `UserServiceClient`'s `connect-timeout`/
`read-timeout` (Milestone 11) bound a single HTTP attempt at the OpenFeign/
HTTP-client layer; `AsyncUserServiceLookup`'s `@TimeLimiter` bounds the total
wall-clock time of the `CompletableFuture` it wraps — which, in this
composition, is the entire Retry-driven sequence of up to 3 attempts, each
individually bounded by that same read-timeout, plus backoff waits between
them. Sized wrong relative to each other, the two fight: a TimeLimiter timeout
shorter than one attempt's own read-timeout would fire before that attempt
could even time out on its own terms, discarding Retry's remaining attempts
outright (`cancel-running-future: true` makes this explicit — it doesn't just
stop *waiting*, it cancels the in-flight future); sized with headroom above
the natural worst case of the inner chain (this platform's `7s` against an
observed ~5s worst case for 3 Retry attempts), it instead behaves purely as a
backstop — the inner timeouts are what actually fire in the overwhelming
majority of failures, and the outer TimeLimiter only matters if something
about the inner chain's own timing assumptions turns out to be wrong in
production (a backoff multiplier that's drifted, a jitter factor that's
unexpectedly large, a CircuitBreaker still letting attempts through close to
its own threshold) — exactly the kind of defense-in-depth a connect/read
timeout alone can't provide, since it has no concept of "the whole operation,"
only "this one attempt."

🔥 **Chaos Lab — try it yourself:**

*Bulkhead exhaustion (`ExchangeRateClient`):*
1. Temporarily lower `exchangeRateLookup`'s pool in
   `transaction-service/src/main/resources/application.yml` to something a
   handful of concurrent requests can actually saturate —
   `core-thread-pool-size: 1`, `max-thread-pool-size: 1`, `queue-capacity: 1`
   — and rebuild: `docker compose up -d --build transaction-service`.
2. `source scripts/smoke-env.sh`, then fire several concurrent transactions
   whose `currency` differs from the test user's `preferredCurrency` (check
   it first: `curl -s $GW/api/v1/users/$USER_ID -H "$AUTH" | jq .preferredCurrency`
   — use anything else, e.g. `"USD"` if that returns `"INR"`):
   ```bash
   for i in {1..6}; do
     curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/transactions \
       -H "$AUTH" -H "Content-Type: application/json" \
       -d "{\"userId\":\"$USER_ID\",\"categoryId\":\"$CATEGORY_ID\",\"amount\":5,\"type\":\"EXPENSE\",\"description\":\"bulkhead-chaos\",\"transactionDate\":\"2026-10-01\",\"currency\":\"USD\"}" &
   done
   wait
   ```
3. With the pool capped at 1 thread + 1 queue slot, several of the six
   concurrent calls should return `503` (`errorCode: DOWNSTREAM_SERVICE_UNAVAILABLE`)
   almost immediately — `BulkheadFullException` rejecting outright rather than
   waiting — while at most two succeed (one running, one queued).
   `docker compose logs transaction-service --since 2m | grep -i "exchange-rate lookup fell back"`
   shows the fallback firing for the rejected calls.
4. Revert the pool sizes (`5`/`10`/`20`) and rebuild before moving on.

*TimeLimiter timeout (`AsyncUserServiceLookup`):*
1. In `user-service`'s `UserController.getById`, temporarily add
   `Thread.sleep(8000);` at the top of the method body (longer than both the
   1.5s read-timeout per attempt AND the 7s outer `TimeLimiter`) and rebuild:
   `docker compose up -d --build user-service`.
2. Send one authenticated transaction request (any currency) through the
   gateway and time it:
   ```bash
   source scripts/smoke-env.sh
   time curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/transactions \
     -H "$AUTH" -H "Content-Type: application/json" \
     -d "{\"userId\":\"$USER_ID\",\"categoryId\":\"$CATEGORY_ID\",\"amount\":5,\"type\":\"EXPENSE\",\"description\":\"timelimiter-chaos\",\"transactionDate\":\"2026-10-01\",\"currency\":\"INR\"}"
   ```
3. Expect `503` at just over **7s**, not ~5s (Retry's own 3-attempt worst case
   — each attempt now always "succeeds" from the HTTP client's point of view
   right up until the artificial 8s sleep would eventually respond, so Retry
   has nothing to retry on; without `@TimeLimiter`, this request would instead
   hang for the full 8s+ per attempt, up to 24s+ across 3 retries) and well
   under what an unbounded wait would take. `docker compose logs
   transaction-service --since 2m | grep -i "async user-service lookup fell back"`
   shows the `TimeoutException` the fallback converted.
4. Revert the `Thread.sleep(8000)` line and rebuild user-service before
   moving on.

### Validating Milestone 13

1. **Structural checks** — `xmllint --noout` on every `pom.xml`; parse every
   touched YAML (`config-repo/application.yml`, `transaction-service/
   application.yml`, `api-gateway.yml`, both Prometheus files,
   `docker-compose.override.ide.yml`) with PyYAML; `docker compose -f
   docker-compose.yml -f docker-compose.override.ide.yml config -q`; `javac`
   syntax-only pass on every new/changed `.java` file (this sandbox has no
   Maven Central access, so a full `mvn compile` isn't possible here — only
   missing-symbol errors from the absent classpath are expected, zero syntax
   errors).
2. **Rebuild and bring the stack up**:
   ```bash
   docker compose up -d --build transaction-service user-service api-gateway
   ```
   (`user-service` only needs rebuilding if you ran the Chaos Lab steps above
   and are now reverting them.)
3. **Confirm the happy path still works** and now carries the new fields:
   ```bash
   source scripts/smoke-env.sh
   curl -s -X POST $GW/api/v1/transactions \
     -H "$AUTH" -H "Content-Type: application/json" \
     -d "{\"userId\":\"$USER_ID\",\"categoryId\":\"$CATEGORY_ID\",\"amount\":100,\"type\":\"EXPENSE\",\"description\":\"m13-happy-path\",\"transactionDate\":\"2026-10-01\",\"currency\":\"INR\"}" | jq .
   ```
   Expect `201` with the response now including `"currency":"INR"` and
   `"baseCurrencyAmount":null` (same-currency — nothing to convert).
4. **Confirm actual currency conversion** — repeat step 3 with a `currency`
   different from the test user's `preferredCurrency` (checked via `GET
   /api/v1/users/{id}`). Expect `201` with `baseCurrencyAmount` populated and
   non-null; a Prometheus check confirms the bulkhead was actually exercised:
   ```bash
   curl -s $GW/actuator/prometheus 2>/dev/null; \
   curl -s http://localhost:8083/actuator/prometheus | grep -i 'resilience4j_bulkhead.*exchangeRateLookup'
   ```
   (run directly against transaction-service's own port — the Gateway has no
   business route to its Actuator — look for
   `resilience4j_bulkhead_available_concurrent_calls` and
   `resilience4j_bulkhead_max_allowed_concurrent_calls` for `exchangeRateLookup`.)
5. **Confirm the two Chaos Lab scenarios above** (Bulkhead exhaustion,
   TimeLimiter timeout) — both are written as pass/fail checks with an
   expected status code and an expected rough timing, not just "observe the
   logs."
6. **Confirm the folded-in pending fixes**, each independently:
   - *LoadBalancer retry disabled*: re-run the TimeLimiter Chaos Lab above and
     confirm the `503` lands at just over 7s (the TimeLimiter ceiling), not
     14-20s+ (which would indicate LoadBalancer is still silently multiplying
     attempts underneath Resilience4j's own 3).
   - *Field-level validation errors*: send a deliberately invalid request
     (e.g. omit `currency` and send a negative `amount`) and confirm the `400`
     body's `errors` array lists both fields with their own messages, not just
     a generic `"Invalid request content."` detail string.
   - *UTC/IST `@PastOrPresent` fix*: if testing between roughly 00:00-05:30
     IST, submit `transactionDate` as today's date in IST and confirm `201`,
     not a `400` — this was the exact window the bug fired in before the fix.
   - *Duplicate `X-Correlation-ID`*: `curl -i` any endpoint through the
     gateway and confirm exactly one `X-Correlation-ID` header line in the
     response, not two.
   - *Dockerfiles*: `docker exec spendwise-transaction-service whoami` should
     print `spendwise`, not `root`; a second `docker compose up -d --build
     transaction-service` after no source changes should finish noticeably
     faster than the first (cache mount hit).
   - *Prometheus IDE-mode*: with the Option B compose override running, open
     `http://localhost:9090/targets` and confirm the `spendwise-services` job's
     targets resolve (not necessarily all UP, depending on which services you
     actually have running on the host at the time).

**Milestone 14 complete** — Edge Throttling (Redis Token Bucket Rate
Limiting). No fixes were raised validating Milestone 13, so nothing folds in
here beyond this milestone's own scope — but live validation of Milestone 14
itself surfaced a real bug in its first implementation, corrected in the same
milestone rather than deferred (see "Found and fixed during validation"
below); what's described here is the corrected, working version.

Scoped exactly to the roadmap's two sentences: spin up Redis, and configure a
Redis-backed token-bucket filter directly inside api-gateway so a burst never
reaches any core service. No per-user tiers, no multiple rate-limit zones, no
IP allowlist — none of that is in the roadmap text, so none of it is here.

Rather than hand-write a Lua-script-based limiter, this uses Spring Cloud
Gateway's own built-in `RedisRateLimiter` (confirmed against its
[reference documentation](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/gatewayfilter-factories/requestratelimiter-factory.html)
to actually implement the Token Bucket Algorithm the roadmap names, not
assumed): adding `spring-boot-starter-data-redis-reactive` to `api-gateway`'s
classpath is what makes `RedisRateLimiter` available at all. `RouteConfig`
applies it to *every* route it declares — including the docs-proxy loop, not
just the business/auth routes — matching "before they reach any core
service" rather than "before they reach auth-service."
`REPLENISH_RATE: 10` / `BURST_CAPACITY: 20` / `REQUESTED_TOKENS: 1`
(`RateLimitConfig`'s `RedisRateLimiter` bean constructor) mirror Spring's own
documented example values for these same three parameters.

**`RateLimitConfig`** (new, `api-gateway/config`) supplies the two pieces
Spring Cloud Gateway can't default sensibly on its own: the `RedisRateLimiter`
bean itself (its three-`int` constructor — replenishRate, burstCapacity,
requestedTokens — works as a plain `@Bean` because `RedisRateLimiter`
implements `ApplicationContextAware` and looks up its own
`ReactiveStringRedisTemplate`/`RedisScript` from the context the moment
Spring calls `setApplicationContext` on it, confirmed directly against its
[source](https://github.com/spring-cloud/spring-cloud-gateway/blob/main/spring-cloud-gateway-server/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/RedisRateLimiter.java)),
and the `KeyResolver` bean `RouteConfig` passes alongside it. The built-in
default `KeyResolver`, `PrincipalNameKeyResolver`, keys on the authenticated
principal — useless for exactly the two endpoints this milestone's own
threat model (brute-force) centers on, `POST /api/v1/auth/login` and
`/register`, both `permitAll()` and therefore carrying no principal at all;
worse, Spring Cloud Gateway denies a request outright when its `KeyResolver`
yields no key, so a principal-keyed resolver would end up blocking all
unauthenticated traffic rather than throttling it. `remoteAddressKeyResolver`
keys on client IP instead — the one identity every request has, authenticated
or not — with a named fallback key (`"unresolved-remote-address"`) rather
than `Mono.empty()` for the rare case the remote address itself can't be
resolved, so that edge case degrades to one shared bucket instead of an
outright, confusing deny.

Redis itself gets an explicit `spring.data.redis.timeout: 1000ms`
(config-repo/api-gateway.yml) for the same reason every other external
dependency in this platform has one (Milestone 11's Feign defaults,
Milestone 13's exchange-rate `WebClient`): `RedisRateLimiter`'s default
behavior on a clean Redis error is to fail open (allow the request through
rather than block the Gateway on it — community-confirmed against
[spring-cloud-gateway#886](https://github.com/spring-cloud/spring-cloud-gateway/issues/886),
since the reference docs don't spell this out explicitly), but a *degraded*,
not cleanly-down, Redis is a different failure mode entirely — a real,
reported case ([spring-cloud-gateway#3373](https://github.com/spring-cloud/spring-cloud-gateway/issues/3373))
where every gated request queued behind a slow Lua-script round trip rather
than being quickly allowed or denied, because nothing bounded that round
trip's own latency. The explicit timeout is what turns "degraded Redis hangs
the whole Gateway" into "degraded Redis fails open quickly," consistent with
this platform's one rule for every dependency it doesn't own: never wait on
it unbounded.

**Found and fixed during validation — `default-filters` is a no-op for
Java-DSL routes.** The first implementation put the `RequestRateLimiter`
filter in `spring.cloud.gateway.default-filters` (config-repo/api-gateway.yml)
rather than in code, on the reasonable-sounding assumption that a
default-filter applies platform-wide regardless of how a route is declared.
Live validation disproved it immediately and unambiguously: 25 requests fired
at `/api/v1/auth/login` past the configured burst capacity of 20 all still
returned `401` (never `429`), and `redis-cli KEYS 'request_rate_limiter.*'`
came back completely empty — meanwhile `/actuator/health` showed Redis itself
as `UP`, proving the connection worked and the filter simply never ran.
Root cause, confirmed against a maintainer-acknowledged report of the exact
same mismatch
([spring-cloud-gateway#3121](https://github.com/spring-cloud/spring-cloud-gateway/issues/3121)):
`default-filters` only attaches to routes sourced from a
`RouteDefinitionLocator` (YAML/properties-defined routes) — every route in
this platform, since Milestone 6, is instead built via `RouteLocatorBuilder`'s
Java fluent DSL in `RouteConfig`, which `default-filters` silently never
touches. The fix moved the filter into `RouteConfig` itself: a shared private
`rateLimited(...)` helper applies `.requestRateLimiter(c ->
c.setRateLimiter(redisRateLimiter).setKeyResolver(keyResolver))` to every
declared route, and `RedisRateLimiter`'s token-bucket values moved from inert
YAML filter args onto its own bean constructor in `RateLimitConfig` (the
filter's own `Config` object exposes only `setRateLimiter`/`setKeyResolver`
in the fluent API, not the rate values themselves — confirmed against a
working [Baeldung reference example](https://www.baeldung.com/spring-cloud-gateway-rate-limit-by-client-ip)
and [spring-cloud-gateway#2246](https://github.com/spring-cloud/spring-cloud-gateway/issues/2246)).
The inert YAML block was removed rather than left alongside the real fix,
the same call Milestone 13 made for `DedupeResponseHeader` when it turned out
not to work either.

**Q51: How do you implement rate limiting / throttling at the gateway
level?** By making the gateway itself the enforcement point, before a request
ever reaches a core service — exactly what `RouteConfig`'s shared
`requestRateLimiter` filter does here, applied to every route rather than
one. Three pieces are needed: an algorithm and its state (this
platform uses `RedisRateLimiter`'s token bucket, with the bucket's token
count held in Redis rather than in-process, so the limit holds correctly
across every api-gateway instance sharing that Redis, not per-instance); a
key to bucket requests by (`KeyResolver` — IP here, since the threat model is
pre-authentication brute-force, though a principal or API-key resolver suits
a different threat model equally well); and a response when the bucket is
empty (`429 Too Many Requests` by default, not a silent drop or a hang,
giving a well-behaved client an unambiguous, retryable signal).

**Q52: Token bucket vs. leaky bucket vs. fixed window — how do they differ
under burst traffic?** All three cap *average* rate, but each treats a short
burst differently. **Fixed window** (count requests in discrete window, e.g.
clock-aligned 1-second buckets, reset to zero each new window) is the
simplest and cheapest, but allows up to 2x the configured rate across a
window *boundary* — a burst at 23:59:59.9 followed by another at 00:00:00.1
both land inside their own empty window and both pass, even though they're
100ms apart. **Leaky bucket** (requests queue into a bucket that drains at a
constant rate; the bucket size bounds how much can queue, not how fast a
burst is admitted) smooths output to a strictly constant rate — it eliminates
bursty *downstream* traffic entirely, at the cost of added latency for
anything that queues rather than being rejected outright. **Token bucket**
(tokens refill continuously at `replenishRate`; a request is admitted
instantly if a token is available, up to `burstCapacity` tokens banked) is
the middle ground this milestone uses: it allows a genuine, legitimate burst
up to the bucket's full capacity to pass through with zero added latency
(unlike leaky bucket, which would queue/delay it), while still bounding the
*sustained* long-run rate to `replenishRate` once the banked tokens are
spent — not a flat per-window cap that resets predictably like fixed window,
which makes it harder for an attacker to time a burst around a window
boundary the way fixed window allows.

🔥 **Chaos Lab — try it yourself:**

1. `source scripts/smoke-env.sh` to get an authenticated `$AUTH` and
   `$USER_ID`/`$CATEGORY_ID` for comparison, then hammer the **unauthenticated**
   login endpoint past `burstCapacity` (20) from the same IP in a tight loop:
   ```bash
   for i in {1..25}; do
     curl -s -o /dev/null -w "%{http_code} " -X POST $GW/api/v1/auth/login \
       -H "Content-Type: application/json" \
       -d '{"email":"nobody@spendwise.dev","rawPassword":"wrong"}'
   done
   echo
   ```
   Expect the first ~20 to return `401` (a genuinely wrong password, correctly
   rejected by auth-service) and the remainder to flip to `429` — the token
   bucket empties, and the Gateway itself starts rejecting before auth-service
   ever sees the remaining attempts.
2. Confirm it's actually Redis holding the bucket, not in-memory per-instance
   state: `redis-cli -h localhost -p 6379 KEYS 'request_rate_limiter.*'` shows
   the keys `RedisRateLimiter`'s Lua script maintains, keyed by the IP
   `remoteAddressKeyResolver` resolved.
3. Wait a few seconds (tokens refill at `replenishRate: 10`/sec) and repeat a
   single request — it succeeds again (`401`, not `429`), confirming this is a
   genuine leaky/refilling bucket, not a one-time lockout.
4. Confirm an authenticated, already-passing-traffic route is governed by the
   *same* bucket (one shared filter, applied platform-wide, not a login-only
   special case) by immediately following the burst above with one authenticated
   call from the same IP:
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" $GW/api/v1/users/$USER_ID -H "$AUTH"
   ```
   If step 1's burst hasn't yet refilled past `requestedTokens: 1`, this also
   returns `429` — the bucket is per-IP across every route, not per-route.

### Validating Milestone 14

1. **Structural checks** — `xmllint --noout api-gateway/pom.xml`; parse
   `config-repo/api-gateway.yml` and `docker-compose.yml` with PyYAML;
   `docker compose config -q`; `javac` syntax-only pass on `RateLimitConfig.java`
   (as with every milestone in this sandbox, a full `mvn compile` isn't
   possible here — only missing-symbol errors from the absent classpath are
   expected, zero syntax errors).
2. **Rebuild and bring the stack up**, including the new `redis` service:
   ```bash
   docker compose up -d redis
   docker compose up -d --build api-gateway
   ```
3. **Confirm Redis is actually reachable and the bean wiring works** —
   `docker compose logs api-gateway --since 2m | grep -i redis` should show no
   connection errors, and `GET $GW/actuator/health` should report `UP` (a
   broken Redis connection surfaces there via Spring Boot's auto-configured
   Redis health indicator).
4. **Confirm the happy path is unaffected** — a normal, low-rate sequence of
   authenticated calls through the Gateway (e.g. re-run Milestone 13's own
   happy-path check) should see no `429`s at all; this is a regression check
   that the filter isn't so aggressive it interferes with ordinary traffic.
5. **Confirm the Chaos Lab scenario above** end-to-end — the burst past 20
   flips to `429`, the Redis keys are visible, the bucket refills over time,
   and the same bucket governs an authenticated route too. These are written
   as pass/fail checks with an expected status code, not just "observe the
   logs."
6. **Confirm the fail-open behavior documented above** (optional, more
   involved): `docker compose stop redis`, then send one request through any
   route. Expect it to still succeed (not hang, not `5xx`) within roughly
   `spring.data.redis.timeout` (1s) — proving the explicit Redis timeout is
   actually bounding the degraded-Redis case, not just configured and unused.
   `docker compose start redis` afterward before moving on.

**Milestone 15 complete** — Protocol Diversity: gRPC & Reactive WebFlux. No
fixes were raised validating Milestone 14 beyond its own dedicated fix commit
(already folded in there, not deferred), so nothing folds in here beyond this
milestone's own scope.

Scoped exactly to the roadmap's two sentences: a second internal transport
(gRPC, Protobuf contract) alongside REST, budget-service exposing it and
analytics-service calling it for a low-latency internal query with a
REST-vs-gRPC payload/latency benchmark; and, separately, analytics-service
rebuilt on Spring WebFlux + R2DBC rather than Spring MVC/JPA — Postgres/R2DBC
chosen over the roadmap's other explicitly-offered option (a reactive MongoDB
driver), since this platform already runs one Postgres instance with
schema-per-service isolation for every other `*_db`, and introducing MongoDB
as a second datastore technology for one service's data would be exactly the
kind of unrequested substep the roadmap doesn't ask for.

**The gRPC server side — `budget-service`.** `src/main/proto/budget_summary.proto`
defines `BudgetSummaryService.GetSummary`, a field-for-field mirror of
`BudgetController#summary`'s existing `GET /api/v1/budgets/{userId}/summary`
(same `userId`/`periodMonth` inputs, same response shape) — monetary fields
are encoded as `string`, never a numeric protobuf type, for the same
no-binary-floating-point-for-currency reason this platform never uses
`double`/`float` for money anywhere else, and it also keeps the benchmark
honest: both wire formats encode the same amount as text, so the comparison
measures transport/encoding efficiency, not a format change. **`BudgetSummaryGrpcService`**
(new, `budget-service/grpc`) is this annotation's exact server-side analogue
of `@RestController`: `net.devh`'s `@GrpcService` registers it onto a second,
Netty-based gRPC server listening on its own port (`grpc.server.port: 9090`,
config-repo/budget-service.yml) — entirely separate from Tomcat's own 8084,
since gRPC's HTTP/2-trailers-based framing isn't something the Servlet stack
speaks. It delegates straight to the same `BudgetService.summary(...)` bean
`BudgetController` itself calls, so the two transports can never drift in
behavior; the one thing it does itself is map failure into gRPC's own
`Status` codes (`INVALID_ARGUMENT` for a malformed `userId`/`periodMonth`)
rather than reusing `GlobalExceptionHandler`, whose `@RestControllerAdvice`
machinery a gRPC call never passes through at all. Server reflection
(`grpcurl list`/`describe` without shipping the `.proto` to whoever's
testing) comes free from `grpc-services` being on the classpath — net.devh
enables it by default (`reflection-service-enabled` defaults to `true`,
confirmed directly against
[net.devh's own `GrpcReflectionServiceAutoConfiguration` source](https://github.com/yidongnan/grpc-spring-boot-starter/blob/master/grpc-server-spring-boot-autoconfigure/src/main/java/net/devh/boot/grpc/server/autoconfigure/GrpcReflectionServiceAutoConfiguration.java)),
so no extra property was added for it. `grpc.version=1.68.1`/
`protobuf.version=3.25.5`/`protobuf-maven-plugin.version=0.6.1` (parent
`pom.xml`) are not an independently-guessed trio: they're the exact,
maintainer-pinned combination
[grpc-java's own official examples module uses at tag v1.68.1](https://github.com/grpc/grpc-java/blob/v1.68.1/examples/pom.xml),
since grpc-java and protobuf-java ship on separate release cadences and an
uncoordinated pairing is a real, common source of codegen/runtime mismatches.
`grpc-netty-shaded`, not plain `grpc-netty`, avoids a Netty-version collision
with the Reactor Netty this same milestone puts on analytics-service's
classpath. The new `io.grpc:grpc-bom` import in the parent `pom.xml`'s
`dependencyManagement` is what lets both budget-service (server) and
analytics-service (client) resolve every `io.grpc:*` artifact to the
identical grpc-java release, the same role `spring-cloud-dependencies`
already plays for every `spring-cloud-starter-*` artifact.

**Why the `.proto` is duplicated, not shared via `spendwise-common`.** This
platform's own established convention since Milestone 10 (see
`UserServiceClient`/`UserExistenceResponse`) is that a service consuming
another service's contract owns its own independent copy of exactly what it
needs, rather than sharing contract types across services —
`spendwise-common` is reserved for cross-cutting infrastructure, never
business/domain-shaped contracts, so each service's module stays
polyrepo-extractable without carrying a shared-contract dependency along with
it. A generated gRPC stub is no different in kind from a hand-written Feign
interface in this respect — it simply can't be hand-copied, since the wire
format is codegen'd — so the `.proto` *source* is what's duplicated instead
(byte-for-byte identical in both `budget-service/src/main/proto/` and
`analytics-service/src/main/proto/`), and each service's own
`protobuf-maven-plugin` execution compiles its own copy independently. The
two must be kept in sync by hand if this contract ever changes — the same
maintenance cost duplicating `UserExistenceResponse` already carries,
accepted for the same reason.

**The gRPC client side and WebFlux rebuild — `analytics-service`.**
`pom.xml` is rebuilt wholesale: `spring-boot-starter-web` →
`spring-boot-starter-webflux`, `springdoc-openapi-starter-webmvc-ui` →
`-webflux-ui` (the webmvc-flavored springdoc starter assumes a
`DispatcherServlet` and simply never registers its routes under WebFlux's
`DispatcherHandler`), plus `spring-boot-starter-data-r2dbc`,
`r2dbc-postgresql`, a bare JDBC `postgresql` driver (Flyway-only, see below),
`flyway-core`/`flyway-database-postgresql`, and
`net.devh:grpc-client-spring-boot-starter` + `grpc-protobuf`/`grpc-stub`/
`grpc-netty-shaded`. The async stub, never the blocking one, is injected into
the new **`BudgetSummaryGrpcClient`** (`analytics-service/grpc`): a blocking
stub's call parks the calling thread until the response arrives, which —
called from a WebFlux request-handling thread — would tie up one of Netty's
small, shared event-loop threads for the full round-trip latency of every
dashboard request, defeating the entire point of this rebuild (Q54/Q55).
`Mono.create` bridges the stub's `StreamObserver` callback into the single-item
`Mono` the rest of the service composes over — the standard, documented
pattern for wrapping a callback-based async API in Project Reactor.
Discovery was deliberately *not* used for reaching budget-service
(net.devh's own `discovery:///`-scheme, Eureka-integrated option): its
server-side metadata-publishing behavior couldn't be verified in this
sandbox, so `grpc.client.budget-service.address: static://${BUDGET_SERVICE_HOST:localhost}:9090`
(config-repo/analytics-service.yml) uses the same static `host:port`
addressing this platform's own HTTP routes already use (api-gateway's
`config-repo/api-gateway.yml`) — the lower-risk, already-proven-out choice,
not a new one invented for this milestone. `negotiationType: PLAINTEXT`
matches budget-service's own server, which defaults to plaintext (TLS is
opt-in there via `grpc.server.security.enabled`, left unset) — acceptable
only because this port never leaves the docker-compose network, the same
trust boundary this platform's internal Postgres/Redis/Eureka/Zipkin traffic
already relies on.

**`AnalyticsService`** (new, `service`) composes the one real non-blocking
call chain this milestone exists to prove out: fetch the summary over gRPC,
then — strictly sequenced *after* that succeeds, not in parallel — write a
**`DashboardQueryLog`** row (new, `domain`, via the new reactive
**`DashboardQueryLogRepository`**) recording that the query happened, before
returning the combined **`DashboardResponse`** to **`AnalyticsController`**'s
`GET /api/v1/analytics/dashboard/{userId}?periodMonth=`. `DashboardQueryLog`
is this service's first real persistence, and deliberately leaves `id` null
at construction rather than application-assigning a UUID the way
budget-service's Hibernate-backed `Budget` entity does (`GenerationType.UUID`):
Spring Data R2DBC's default new-vs-existing detection for a reference-typed
`@Id` is "null means new," so the new `V1__create_dashboard_query_log_table.sql`
migration's own `DEFAULT gen_random_uuid()` (built into Postgres core since
version 13 — no `pgcrypto` extension needed on this platform's
`postgres:16-alpine` image) generates the value, and `r2dbc-postgresql`
returns it on the same `INSERT`. **`FlywayMigrationConfig`** (new, `config`)
is the one piece of this rebuild that could not simply follow every JPA-based
service's own auto-configured Flyway bean: Boot's `FlywayAutoConfiguration`
(explicitly excluded on `AnalyticsServiceApplication`) resolves its JDBC
connection from a `DataSource` bean this reactive, R2DBC-only service never
creates, and Flyway itself has no R2DBC support at all — it only ever speaks
JDBC. The fix (confirmed against a working reference implementation of this
exact "R2DBC app, JDBC-only Flyway migration" combination, since Spring
Boot's own reference docs don't cover it) is a manual
`@Bean(initMethod = "migrate") Flyway` that builds its own short-lived JDBC
connection directly from `spring.flyway.*` properties, entirely independent
of the R2DBC `ConnectionFactory` (`spring.r2dbc.url`) the rest of the service
uses to actually serve requests — the two never share a connection pool,
driver, or lifecycle.

**Correlation-id and user-context propagation on the reactive stack.** Every
servlet-based service's `CorrelationIdFilter`/`UserContextFilter`
(spendwise-common) extend `jakarta.servlet.http.HttpFilter` — a type that
doesn't exist on analytics-service's Netty runtime at all, not merely one it
stops using, so simply reusing them was never an option. Two new siblings,
**`ReactiveCorrelationIdFilter`** and **`ReactiveUserContextFilter`**
(spendwise-common, both plain `WebFilter` beans analytics-service's
`TracingConfig`/`UserContextConfig` now register, no `FilterRegistrationBean`
needed — a WebFlux `WebFilter` bean already applies to every request by
construction), do the equivalent job. Neither puts its value into the SLF4J
MDC / `UserContextHolder` `ThreadLocal`s the servlet versions use: a single
WebFlux request is handed across multiple different event-loop threads over
its own lifetime while those same few threads concurrently interleave *other*
requests' work at the same time, so a `ThreadLocal` write with no
same-thread-guaranteed cleanup would leak one request's correlation id (or
caller identity) into some unrelated request's logs the moment that thread
picks up different work next — a hazard this platform's synchronous,
one-thread-per-request servlet services never had to consider. Both instead
write into Reactor `Context` (`contextWrite`), read back out anywhere
downstream — including past a thread-hop — via `Mono.deferContextual(...)`.
**`AbstractReactiveGlobalExceptionHandler`** (spendwise-common, the WebFlux
twin of `AbstractGlobalExceptionHandler`, activated by analytics-service's
own thin `GlobalExceptionHandler` subclass exactly as the servlet version is)
reads the correlation id the same way for its ProblemDetail enrichment, and
overrides `handleWebExchangeBindException` rather than
`handleMethodArgumentNotValid` — WebFlux has no `MethodArgumentNotValidException`
at all, since that type is tied to Spring MVC's own servlet-dispatch
method-argument resolution; a failed `@Valid @RequestBody` binding in WebFlux
throws `WebExchangeBindException` instead, confirmed directly against
spring-webflux's own `ResponseEntityExceptionHandler` source at tag v6.2.1
(the exact Spring Framework version this platform's `spring-boot.version=3.4.1`
BOM pulls in). The one place this milestone's own code *does* cross a real
thread boundary mid-request — `BudgetSummaryGrpcClient`'s gRPC callback,
which runs on gRPC's own executor thread, not analytics-service's
Reactor/Netty thread, so Reactor `Context` itself can't reach it there — uses
a narrowly-scoped `MDC.put`/`remove` bracketing one synchronous log statement
instead; scoped tightly enough around a single callback invocation that it
can never leak into an unrelated request the way a filter-wide `MDC.put`
would. `spendwise-common/pom.xml` gets `spring-webflux` as a `provided`
dependency (mirroring `spring-webmvc`'s own existing `provided` scope) so
neither framework ever becomes a transitive dependency of a service on the
other stack. The HMAC verify/parse logic `UserContextFilter` previously
implemented as private methods is extracted into a new shared
**`UserContextVerifier`** (spendwise-common) so this security-sensitive
signature-checking code has exactly one implementation, called by both the
servlet and reactive filters, rather than two copies that could silently
drift apart.

**Q53: REST vs. gRPC vs. messaging — how do you choose for a given internal
call?** By what the call actually needs, not a platform-wide default. REST/
JSON is the right default for anything public-facing or cross-team: human-
readable, universally tooled, and this platform already standardizes on it
for every external-facing endpoint. gRPC earns its added complexity
(Protobuf schema management, a second port/transport to operate, worse
human-debuggability on the wire) specifically for internal, high-volume,
latency-sensitive service-to-service calls where its binary framing and
HTTP/2 multiplexing measurably pay for themselves — exactly this milestone's
Analytics→Budget call, benchmarked below. Messaging (Kafka, not yet on this
platform) is the right choice instead of either when the caller doesn't need
an immediate response at all — fire-and-forget or eventually-consistent
work — which neither REST nor gRPC's synchronous request/response shape fits
well.

**Q54: What is backpressure, and how does WebFlux handle a slow consumer?**
Backpressure is the mechanism by which a slow consumer tells a fast producer
to slow down, rather than the producer overwhelming it with unbounded,
buffered work. WebFlux's Reactive Streams foundation makes this an explicit
part of the subscription contract: a `Subscriber` calls `request(n)` to pull
exactly `n` items it's ready to handle, and a well-behaved `Publisher` (every
Reactor operator, and Spring Data R2DBC's own reactive driver) never emits
more than that outstanding demand — the opposite of a `List`/blocking
`Iterable`, which has no way to say "not yet." This milestone's own call
chain demonstrates the model without needing to exercise it under real load:
`BudgetSummaryGrpcClient`'s `Mono.create` only ever emits one item for one
subscriber (a unary RPC), so there's exactly one unit of demand to satisfy —
backpressure matters once a `Flux` of many items (a streaming RPC, or a
`Flux<DashboardQueryLog>` read) is involved, which this milestone's single
dashboard-lookup endpoint deliberately doesn't require.

**Q55: Servlet stack vs. WebFlux — what actually changes at the thread-model
level, and when is reactive not worth the complexity?** Tomcat's servlet
model dedicates one thread per in-flight request for its entire lifetime,
blocking that thread for however long any I/O call (a JDBC query, a Feign
call) takes — true of every other service on this platform, which is fine
because their thread pools are sized for their own load. WebFlux instead
runs a small, fixed pool of event-loop threads (Reactor Netty) that are never
blocked: a thread picks up a request, hands off the moment it would block on
I/O, and picks up other work in the meantime, resuming the original request
when its I/O completes — exactly why `BudgetSummaryGrpcClient` must use the
async, not blocking, gRPC stub, and why R2DBC (not JPA) backs
`DashboardQueryLog`; a single blocking call anywhere in a WebFlux chain stalls
one of the only few threads the whole server has, far more damaging than the
same block on a servlet thread pool sized in the hundreds. The trade-off: every
dependency in the chain must actually be non-blocking end to end for this to
pay off — a reactive controller calling even one blocking JDBC/Feign call
gains nothing but debugging complexity (stack traces across `Mono`/`Flux`
chains are harder to read than a straight-line servlet stack trace) while
losing none of the risk, which is exactly why this platform rebuilds only
analytics-service, not every service, onto WebFlux — the other services' I/O
patterns don't currently justify the switch.

🔥 **Chaos Lab — try it yourself (REST vs. gRPC payload/latency benchmark):**

1. Seed a few budgets for one user via the REST API (`POST /api/v1/budgets`,
   several categories, same `periodMonth`), then compare the two transports
   serving the *same* underlying data:
   ```bash
   # REST (via the Gateway, JSON over HTTP/1.1)
   time curl -s $GW/api/v1/budgets/$USER_ID/summary?periodMonth=2026-09 -H "$AUTH" -o /tmp/rest-summary.json
   wc -c /tmp/rest-summary.json

   # gRPC (direct to budget-service's own port, bypassing the Gateway —
   # there is no gRPC route in RouteConfig, by design: this is an internal,
   # service-to-service transport, never exposed externally)
   time grpcurl -plaintext -d "{\"user_id\":\"$USER_ID\",\"period_month\":\"2026-09\"}" \
     localhost:9090 spendwise.grpc.budget.BudgetSummaryService/GetSummary | tee /tmp/grpc-summary.json
   wc -c /tmp/grpc-summary.json
   ```
   Expect the gRPC payload to come back smaller (binary field tags vs. JSON's
   repeated string keys) and the round trip faster, more pronounced as the
   number of budget items grows — this is `getSerializedSize()`-class savings
   from Protobuf's binary framing, not a difference in the data itself.
2. Exercise the actual new endpoint end to end: `curl $GW/api/v1/analytics/dashboard/$USER_ID?periodMonth=2026-09 -H "$AUTH"`
   should return the same items the REST summary above does, proving
   analytics-service's own gRPC call to budget-service round-tripped
   correctly — then `docker exec spendwise-postgres psql -U spendwise -d analytics_db -c "SELECT * FROM dashboard_query_log ORDER BY queried_at DESC LIMIT 1;"`
   should show the query just logged.
3. `grpcurl -plaintext localhost:9090 list` and
   `grpcurl -plaintext localhost:9090 describe spendwise.grpc.budget.BudgetSummaryService`
   confirm server reflection works out of the box with zero extra
   configuration, and without ever handing the `.proto` file to whoever's
   running these commands.

### Validating Milestone 15

1. **Structural checks** — `xmllint --noout` every touched `pom.xml` (root,
   `budget-service`, `analytics-service`, `spendwise-common`); parse every
   touched `config-repo/*.yml` and `docker-compose.yml` with PyYAML;
   `docker compose config -q`; `javac` syntax-only pass on every new/changed
   `.java` file (as with every milestone in this sandbox, a full `mvn compile`
   isn't possible here — no Maven Central access, so this one check could
   only confirm the absence of real syntax errors, never that the protobuf
   codegen or the full dependency graph actually resolves). **This is the one
   milestone where that gap matters far more than usual**: unlike every prior
   milestone's plain Java/Spring code, nothing here can prove the
   `protobuf-maven-plugin` execution itself succeeds, that the generated
   `BudgetSummaryServiceGrpc`/`BudgetSummaryItem` classes actually compile
   against what `BudgetSummaryGrpcService`/`BudgetSummaryGrpcClient` expect of
   them, or that the R2DBC/Flyway wiring actually connects — step 2 below, a
   real `mvn compile`/`docker compose build` on a machine with network
   access, is doing genuine, first-time verification work no prior milestone
   depended on this heavily.
2. **Rebuild every touched module and bring the stack up**, including the new
   `analytics_db` database (created automatically by `infra/postgres-init/01-create-databases.sql`
   on a fresh `postgres` volume — an existing volume needs
   `docker exec spendwise-postgres psql -U spendwise -c "CREATE DATABASE analytics_db;"`
   run by hand once, the same caveat every previous new-database milestone has
   carried):
   ```bash
   docker compose up -d --build budget-service analytics-service
   docker compose logs budget-service --since 2m | grep -iE "grpc|error"
   docker compose logs analytics-service --since 2m | grep -iE "flyway|r2dbc|grpc|error"
   ```
   Confirm Flyway's migration log shows `V1__create_dashboard_query_log_table.sql`
   applied successfully, and no gRPC channel/connection errors on either side.
3. **Confirm both services report healthy** — `GET $GW/actuator/health` for
   analytics-service via its docs-proxy route, or directly,
   `curl http://localhost:8086/actuator/health`, should report `UP`, with the
   `r2dbc` indicator specifically up (a broken R2DBC connection surfaces
   there); same for budget-service at 8084, unaffected by the new gRPC port.
4. **Run the Chaos Lab steps above** — the REST-vs-gRPC payload/latency
   comparison, the dashboard endpoint round trip with its `dashboard_query_log`
   row, and the two `grpcurl` reflection commands. These are written as
   pass/fail checks (payload sizes differ, the query log row appears,
   `grpcurl` returns real service/method names), not just "observe the logs."
5. **Confirm the REST side is completely unaffected** — re-run
   `GET /api/v1/budgets/{userId}/summary` directly (not through analytics-service)
   and confirm it still returns `200` with the same data as before this
   milestone; the new gRPC endpoint is a second transport over the same
   business logic, never a replacement for the first.
6. **Confirm the platform's full existing test suite still passes** —
   `mvn -pl budget-service,analytics-service,spendwise-common -am test`
   — the extracted `UserContextVerifier` and the rebuilt `GlobalExceptionHandler`/
   `TracingConfig`/`UserContextConfig` in analytics-service are refactors of
   working cross-cutting code, not new business logic, so this is primarily a
   regression check that nothing silently broke in the extraction.

**Milestone 16 complete** — Atomic State Processing: The Transactional Outbox
Pattern. Three fixes were raised validating Milestone 15, all already applied
directly on `main` before this milestone started (`2b79a62`: `javax.annotation
-api` provided-scope dependency for grpc-java's generated `@Generated`
annotation on Java 21; `87c87c0`: removed budget-service's colliding
`"9090:9090"` host-port mapping against Prometheus's own pre-existing one;
`cc81582`: fixed `UserContextPropagationGlobalFilter`'s `Mono<Void>`/
`switchIfEmpty` misuse that invoked the downstream filter chain, and with it
route proxying and the rate limiter, twice per request). This milestone
builds on top of all three rather than re-deriving them. One further,
non-functional fix folds in here too: `docker-compose.yml`'s comment above
budget-service's gRPC port block still described host-reachable gRPC after
`87c87c0` removed the line that provided it — corrected, and the port is
republished as `"9095:9090"` (host 9095, container port unchanged at 9090) so
gRPC stays reachable from the host for this milestone's own Postman-based
validation, on a host port confirmed free against every other mapping in this
file.

Scoped exactly to the roadmap's own two sentences: the write path from
Milestone 1 (`POST /api/v1/transactions`) now also writes a durable,
queryable outbox row in the same local database transaction as the
`Transaction` row itself — nothing else. No Kafka broker, producer, or
consumer exists anywhere on this platform yet; that is Milestone 17's own
scope, confirmed directly against the locked roadmap's Milestone 13-20 text
before writing a line of this milestone's code, specifically to avoid the
overreach of building a producer or a consumer this milestone doesn't ask
for. `TransactionCreatedEvent` is created now, ahead of any consumer, only
because the roadmap itself names it ahead of its own Milestone 17/19 uses.

**`V4__create_outbox_events_table.sql`** (new,
`transaction-service/.../db/migration`) adds `outbox_events`
(`id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload`,
`created_at`, `processed_at`) plus a partial index,
`idx_outbox_events_unprocessed`, on `created_at` filtered to
`processed_at IS NULL` — the exact "find unprocessed rows, oldest first"
access pattern a future poller needs, present now because it is this table's
own natural index, not a piece of Milestone 17 built early. The column shape
itself is not invented for this platform: it matches Debezium's own
documented "outbox event router" convention (`aggregate_type`/`aggregate_id`
identify *what* changed, independently of *what kind* of event it was),
chosen so this table's shape means the same thing here as it does everywhere
else this pattern is used.

**`OutboxEvent`** (new, `domain`) is the JPA entity over that table —
`GenerationType.UUID` for its own `id` (application-assigned, same
convention `Transaction`/`Category`/`Budget` already use on this platform's
JPA-backed services), a protected no-arg constructor JPA requires, and a
public four-argument constructor (`aggregateType`, `aggregateId`,
`eventType`, `payload`) that stamps `createdAt = Instant.now()` itself so
every caller gets a consistent timestamp without having to supply one.
`processedAt` is mapped but written by nothing in this milestone — the
column, and the getter for it, exist now because they are this row's own
fields, not because the poller that will eventually set them is being built
ahead of schedule. **`OutboxEventRepository`** (new, `domain`) is a bare
`JpaRepository<OutboxEvent, UUID>` with no custom query methods — this
milestone only ever calls `save(...)`; a `findUnprocessed(...)`-shaped query
method belongs to Milestone 17's poller, which is the thing that would
actually call it.

**`TransactionCreatedEvent`** (new, its own `event` package,
`transaction-service`) is a plain record — `transactionId`, `userId`,
`categoryId`, `amount`, `type`, `transactionDate`, `currency`,
`baseCurrencyAmount`, `createdAt` — carrying everything a future consumer
would need without having to call back into transaction-service for it. It
is deliberately *not* a member of a sealed-interface event hierarchy the
roadmap's own notes sketch for later listener-side pattern matching: no
listener exists anywhere on this platform yet to pattern-match against, so
building that hierarchy now would itself be exactly the kind of unrequested
substep this milestone's scope doesn't ask for — it arrives the milestone a
real consumer needs it. It is also deliberately local to transaction-service,
not `spendwise-common`: this platform's own established convention since
Milestone 10 (`UserExistenceResponse`) and reaffirmed at Milestone 15 (the
duplicated `budget_summary.proto`) keeps `spendwise-common` free of
business/domain-shaped contracts, reserving it for cross-cutting
infrastructure only.

**`TransactionService`** (modified, `service`) gets the one behavioral
change this milestone makes: `create(...)` now calls a new private
`recordOutboxEvent(Transaction saved)` immediately after
`transactionRepository.save(...)`, still inside the same `@Transactional`
method — the entire point, since a crash between two separately-committed
writes to two different systems (Postgres and, eventually, Kafka) is exactly
the dual-write antipattern this pattern removes (`OutboxEvent`'s own Javadoc
carries the full failure-mode walkthrough). `recordOutboxEvent` builds a
`TransactionCreatedEvent` from the just-saved `Transaction`, serializes it
with the already-autoconfigured `ObjectMapper` (new constructor dependency),
and saves an `OutboxEvent` row with `aggregateType = "Transaction"`,
`aggregateId = saved.getId()`, `eventType = "TransactionCreatedEvent"`. The
checked `JsonProcessingException` `ObjectMapper#writeValueAsString` can throw
is caught and rethrown as an unchecked `IllegalStateException`: every field
on `TransactionCreatedEvent` is a Jackson-trivial type (UUID, `BigDecimal`,
an enum, a date/instant) with no custom serializer that could actually fail
for this payload shape, so this is the programming-error case, not a
recoverable business outcome — not worth adding a checked exception to this
method's (and in turn `TransactionController#create`'s) signature for a
failure mode that cannot occur here.

**`docker-compose.yml`** (modified) — the budget-service gRPC port
correction described above; no other service's configuration changes, since
this milestone adds no new infrastructure dependency (no new database, no
new container, no new environment variable).

**Q56: How does the Transactional Outbox pattern solve the dual-write
problem, and what delivery guarantee does it actually provide?** The
dual-write problem is that "commit to the database" and "publish to a
message broker" are two separate operations against two unrelated systems
with no shared transaction — there is no way to make them atomic, so a crash
between them either loses the event (DB commits, the broker call never
happens or fails) or fabricates one with nothing behind it (the broker call
succeeds, the DB transaction then rolls back). The outbox pattern sidesteps
this by never making the broker call from inside the business transaction at
all: it writes a second, local row — this milestone's `outbox_events` —
describing the event, in the exact same `@Transactional` boundary as the
business write, so the two commit together or neither does; "did this event
really happen" becomes a question the local database transaction alone can
answer. A separate process (Milestone 17's outbox-publisher poller, not
built in this milestone) later scans this table for unprocessed rows and
publishes each one to Kafka, marking it processed only on broker
acknowledgment. That poller can crash, retry, or be redeployed at any point
without losing an event — it will simply find the same unprocessed row again
next time it scans — which is what makes this **at-least-once** delivery,
not exactly-once: a poller that publishes successfully but crashes before
marking the row processed will republish it on its next pass, so a
downstream consumer must itself be idempotent against redelivery (the
platform's own roadmap names this exact concern at Milestone 19's
idempotency layer).

### Validating Milestone 16

This milestone adds no new REST endpoint and no new request field — the
write that matters is the existing `POST /api/v1/transactions`, now also
writing an `outbox_events` row behind the scenes. Starting with this
milestone, validation runs through Postman against containers you start
yourself, not copy-pasted shell one-liners: the commands below are only for
starting the containers and for the one check Postman itself cannot do
(looking directly at a database row).

1. **Rebuild and start the stack**, in Git Bash:
   ```bash
   cd /d/spendwise
   docker compose up -d --build transaction-service
   docker compose logs transaction-service --since 2m | grep -iE "flyway|error"
   ```
   Confirm the log shows `V4__create_outbox_events_table.sql` applied
   successfully, with no errors. If this is a fresh volume, every other
   service needs to be up too for the flows below to work end to end —
   `docker compose up -d --build` with no service name rebuilds and starts
   everything.

2. **Import the Postman collection** — `postman/SpendWise.postman_collection.json`
   from the repo (Postman: File → Import → select the file). This is the
   same collection every milestone since Milestone 5 has shipped and
   extended, not a new one-off file: Milestone 16 only adds a Tests script to
   three existing requests (auto-capturing `userId`, `categoryId`, and the
   new `transactionId` collection variable from each response, so no id ever
   needs hand-copying between requests) and documents the outbox write on
   the existing **Create Transaction** request's own description. Run, in
   order, from the collection:
   - **Auth Service → Business Endpoints → Register** (or **Login** if that
     email is already registered) — mints `accessToken`, though nothing
     below actually requires it, since transaction-service and user-service
     accept direct requests unauthenticated (only the Gateway enforces the
     JWT, per `RouteConfig`'s own design) — included so the flow still
     matches how a real client reaches this platform.
   - **User Service → Business Endpoints → Create User** — sets `userId`.
   - **Transaction Service → Business Endpoints → Create Category** — sets
     `categoryId`.
   - **Transaction Service → Business Endpoints → Create Transaction** — the
     request this milestone actually changes the behavior of. A `201` here
     means the `Transaction` row was written; it does **not** by itself
     prove the outbox row was written too — that is step 3.
   - **Transaction Service → Business Endpoints → List Transactions
     (filtered + paginated)** — confirms the transaction reads back.

3. **Confirm the outbox row itself exists** — the one step Postman cannot
   do, since this milestone deliberately adds no REST endpoint over
   `outbox_events` (see `OutboxEvent`'s own Javadoc for why: nothing reads
   this table yet, so an endpoint over it would have nothing real to expose).
   In Git Bash:
   ```bash
   docker exec -it spendwise-postgres psql -U spendwise -d transaction_db -c "SELECT id, aggregate_type, aggregate_id, event_type, processed_at FROM outbox_events ORDER BY created_at DESC LIMIT 5;"
   ```
   What this does, plainly: `docker exec -it spendwise-postgres` opens an
   interactive session inside the already-running Postgres container (no
   separate SQL client install needed — `psql` ships inside that container's
   own image); `-U spendwise -d transaction_db` connects as the `spendwise`
   user to transaction-service's own database (same user/database this
   service itself connects as, from `docker-compose.yml`); the `-c "..."`
   is the one SQL statement actually being run, asking Postgres to show the
   five most recent outbox rows. Expect one row whose `aggregate_id` matches
   the `id` the Create Transaction response returned (visible in Postman's
   own response pane, or as `{{transactionId}}` under the collection's
   Variables tab), `event_type = TransactionCreatedEvent`, and
   `processed_at` = `NULL` — unprocessed, exactly as expected, since no
   poller exists yet to mark it otherwise. If a GUI is preferred over this
   one command, any Postgres client (DBeaver, TablePlus, pgAdmin) can
   connect to `localhost:5432`, database `transaction_db`, user/password
   `spendwise`/`spendwise` (both published to the host already, per
   `docker-compose.yml`) and browse the `outbox_events` table directly
   instead.

4. **Confirm the atomicity itself, not just the happy path** — send a
   **Create Transaction** request with an intentionally-invalid `categoryId`
   (any random UUID). Expect a `404` (`ResourceNotFoundException`), then
   re-run step 3's query and confirm **no new row** was added to
   `outbox_events` either — proving the outbox write and the business write
   really do share one transaction: a failure before the method returns
   rolls both back together, not just the one that happened to run first.

**Milestone 17 complete** — Decoupled Message Streaming (Apache Kafka
Integration). Scoped to the roadmap's two sentences: a multi-partition Kafka
cluster in Docker Compose, and a background outbox-publisher thread in
transaction-service that reads `outbox_events`, publishes to the
multi-partition `transaction-events` topic, and flags a row complete only on
broker acknowledgment. There are no consumers yet; `@KafkaListener`s in
notification-service and analytics-service are Milestone 18.

Seven fixes raised validating Milestones 15 and 16 fold in here (1–6 and
8), plus one found while building this one (7):

1. **gRPC deadline + 503** — `BudgetSummaryGrpcClient` sets
   `withDeadlineAfter(2s)` per call and maps `UNAVAILABLE`/`DEADLINE_EXCEEDED`
   to `DownstreamServiceUnavailableException` (503 ProblemDetail).
2. **Idempotent database creation** — `infra/postgres-init/01-create-databases.sql`
   rewritten with psql `\gexec` (create only if absent), and run on every
   `docker compose up` by a new one-shot `postgres-db-init` container that all
   database-backed services wait on (`service_completed_successfully`).
3. **Rate-limit key: principal, then IP** — `RateLimitConfig#clientKeyResolver`:
   `user:<JWT sub>` when authenticated, otherwise `ip:<X-Forwarded-For client IP
   | socket address>`, never empty.
4. **Redis down fails fast** — Lettuce `DisconnectedBehavior.REJECT_COMMANDS`
   plus `spring.data.redis.timeout`/`connect-timeout` of 200ms.
5. **429 as RFC 7807 + `Retry-After`** — `ProblemDetailRateLimitFilter` replaces
   the built-in `requestRateLimiter` on every route.
6. **Mockito as `-javaagent`** — `mockito-agent` profile in the parent `pom.xml`.
7. *(found building this milestone)* **analytics-service Gateway route** —
   Milestone 15 added `GET /api/v1/analytics/dashboard/{userId}` and documented
   calling it through the Gateway, but `RouteConfig` never got the route, so
   that call 404'd at the Gateway. Added.
8. **Postman test plan** replaces shell commands for validation from this
   milestone on (`Milestone 17 - Test Plan` folder in the collection).

An independent review of this milestone's diff (needed because it could not be
compiled here, see below) found three more, fixed before handover:

- **postgres healthcheck over TCP** (`pg_isready -h 127.0.0.1`). On a fresh
  volume, the entrypoint runs its init scripts on a socket-only temporary
  server, so the socket check could pass while TCP clients were still refused.
  With fix 2's `postgres-db-init`, that race would have stopped every
  database-backed service from starting after a `down -v`.
- **Over-long correlation id.** `X-Correlation-ID` is client-supplied with no
  length check, and `outbox_events.correlation_id` is `VARCHAR(64)`.
  `TransactionService` now drops a longer value instead of letting the insert
  fail and roll back the whole request as a 500.
- **gRPC target `dns:///` instead of `static://`.** net.devh's static resolver
  resolves the host once, so a budget-service recreated with a new container IP
  was never reached again until analytics-service restarted. grpc-java's DNS
  resolver re-resolves when connections fail.

**The cluster — `docker-compose.yml`.** Three `apache/kafka:3.9.0` nodes in
KRaft mode (no ZooKeeper), each both broker and controller — the layout of
the official image's own multi-node example. Three nodes, not one, because
the topic is created with replication factor 3 and `min.insync.replicas` 2:
an `acks=all` write is acknowledged only once two replicas hold it, so one
broker can be lost without losing an acknowledged event or stalling the
publisher (folder 2 of the test plan stops `kafka-3` to show exactly that).
`auto.create.topics.enable` is off: the topic is provisioned explicitly, so a
mistyped topic name fails instead of silently creating a one-partition,
one-replica topic. Each node is capped at a 256 MB heap and 512 MB container
limit; on a 16 GB laptop running the full stack, the three nodes together
cost about 1.5 GB. `kafka-ui` (kafbat, a browser view of topics, partitions,
messages and their headers) sits behind the `tools` compose profile and
starts only when asked for.

**`OutboxPublisher`** (new, `transaction-service/.../outbox`) — the relay. A
`@Scheduled` poll (every 1 s after the previous one finishes) runs one
`TransactionTemplate` transaction that:

- claims up to 100 unprocessed rows with `SELECT ... FOR UPDATE SKIP LOCKED`
  (**`OutboxEventRepository#lockNextUnprocessedBatch`**, new);
- sends each one with `kafkaTemplate.send(record).get(timeout)`, so the
  thread waits for the broker's `acks=all` acknowledgment;
- calls **`OutboxEvent#markProcessed`** (new) only after that
  acknowledgment, and stops the batch at the first failure.

Rows already acknowledged commit as processed. The failed row and everything
after it stay unprocessed and are claimed again on the next poll, in the
same order. `SKIP LOCKED` means two transaction-service replicas drain
disjoint batches instead of both publishing the same rows — checked against
a real Postgres 16 while building this: a second session claiming while the
first held its locks got the next rows, without blocking. The record key is
`aggregateId`, so all events for one transaction land on one partition, in
order. Headers: `outboxEventId` (the key Milestone 19's idempotency layer will
deduplicate on), `eventType`, `aggregateType`, `X-Correlation-ID`, and the W3C
`traceparent` that `spring.kafka.template.observation-enabled` adds. The topic
is created on the first poll that can reach the cluster, through `KafkaAdmin`
(3 partitions, RF 3, `min.insync.replicas` 2), not at application startup:
transaction-service has to keep accepting writes while Kafka is down — that
is the outbox pattern's whole promise — so Kafka is deliberately not a
startup dependency (`depends_on: service_started`, not `service_healthy`).

**`OutboxPublisherProperties`** (new) binds `spendwise.outbox.publisher.*`
(batch size, topic, partitions, replication, min ISR, send timeout);
**`OutboxPublisherConfig`** (new, `config`) enables scheduling and the
properties. Producer settings live in `config-repo/transaction-service.yml`:
`acks: all`, `enable.idempotence: true` (a producer retry can never write a
record twice to the log), and bounded `max.block.ms`/`request.timeout.ms`/
`delivery.timeout.ms`, so an unreachable cluster can't hold a poll's row locks
for the client defaults of up to two minutes.

**`V5__add_correlation_id_to_outbox_events.sql`** (new) +
**`OutboxEvent`**/**`TransactionService`** (modified) — the outbox row is
written on the request thread but published later on a scheduler thread that
has no request context. Without persisting the request's correlation id on
the row, nothing on the Kafka record could be traced back to the API call
that caused it. `TransactionService` reads it from the MDC (safe on this
one-thread-per-request servlet service) and the publisher puts it on the
record as `X-Correlation-ID`, and into the MDC for the duration of that one
send's log lines only.

**Q57: Event-driven architecture — how do you use Kafka for decoupling
services?** The producer publishes a fact ("transaction created") to a topic
and stops there. It does not know who consumes it, how many consumers there
are, or whether any are running. That removes three couplings a synchronous
call has:

- **Availability** — transaction-service keeps working when every consumer is
  down, and here even when Kafka itself is down (the outbox absorbs the gap).
- **Speed** — a slow consumer lags behind on its own partition offset instead
  of slowing the producer.
- **Change** — Milestone 18 adds two consumers, notification-service and
  analytics-service, with zero changes to transaction-service.

Partitions are the unit of both ordering and parallelism. Keying by aggregate
id gives per-aggregate ordering while spreading load over all three
partitions, which caps a consumer group at three active consumers (Milestone
18's chaos lab). Replication (RF 3, min ISR 2, `acks=all`) is what makes "the
broker acknowledged it" mean "it survives a broker loss". The cost is eventual
consistency and at-least-once delivery: consumers see events after the fact,
and occasionally twice — a publisher that crashes between the broker's ack and
committing `processed_at` republishes on restart. That is why the
`outboxEventId` header exists, and why Milestone 19 builds consumer-side
idempotency.

**Fix 1 — `BudgetSummaryGrpcClient`.** gRPC calls have no deadline unless
the caller sets one, so a budget-service that accepted the connection but never
answered left the dashboard request hanging indefinitely. The deadline is
applied per call, on a fresh stub view: `withDeadlineAfter` fixes an absolute
expiry when it is called, so applying it once to the injected stub would fail
every call after the first two seconds of uptime. `UNAVAILABLE` and
`DEADLINE_EXCEEDED` both mean "the dependency is the problem, retry later",
so both become a 503, the same contract transaction-service's Feign fallbacks
give for user-service. Every other gRPC status is left alone: those are bugs,
and labelling them "temporarily unavailable" would hide them.

**Fix 2 — database creation.** Postgres has no `CREATE DATABASE IF NOT
EXISTS`, and `CREATE DATABASE` can't run inside a `DO` block, so each line is
`SELECT 'CREATE DATABASE x' WHERE NOT EXISTS (...) \gexec`. The SELECT emits
the statement only when the database is missing, and `\gexec` runs what it
emitted. The same file still serves postgres's own initdb hook for a fresh
volume. Checked against a real Postgres 16 while building this: it creates
all five databases on the first run, does nothing on the second, and
recreates `analytics_db` after it is dropped — the exact existing-volume case
that was missed. The manual `CREATE DATABASE analytics_db` step in Milestone
15's validation notes is no longer needed.

**Fix 3 — `RateLimitConfig#clientKeyResolver`.** Keying on IP alone put
every user behind one NAT or corporate proxy into one 20-token bucket.
Authenticated requests are now keyed by JWT subject (`user:` prefix);
anonymous ones (login/register, the brute-force targets) by client IP (`ip:`
prefix). Anonymous-token principals are excluded, so unauthenticated callers
can never share one `user:anonymousUser` bucket. The X-Forwarded-For entry
used is the one at `size - trusted-proxy-hops` (default 1, i.e. one load
balancer in front). It is accepted only if it is an IP literal, and is never
handed to `InetAddress`, which would do a blocking DNS lookup on a Netty
event-loop thread for a hostname-shaped value. **Caveat:** with nothing in
front of the Gateway — as in this compose setup — every X-Forwarded-For value
is client-supplied. A client can rotate it to get a fresh anonymous bucket per
request (test plan folder 6 shows this). Set
`spendwise.rate-limit.trusted-proxy-hops: 0` wherever the Gateway is exposed
directly.

**Fix 4 — Lettuce.** Lettuce's default is to queue commands while
disconnected and replay them on reconnect, so with Redis stopped every
rate-limit check waited out the full command timeout. `REJECT_COMMANDS`
fails the command at once; `RedisRateLimiter` already fails open on any Redis
error. Applied through Boot's `LettuceClientOptionsBuilderCustomizer`, which
runs after Boot has applied the `spring.data.redis.*` timeouts, so those are
kept rather than replaced.

**Fix 5 — `ProblemDetailRateLimitFilter`** (new, `api-gateway/.../filter`).
The built-in filter rejects with `setComplete()`: an empty 429 with no hint of
when to retry, and no extension point on that branch. The new filter performs
the same three steps — resolve the key, ask the same `RedisRateLimiter` bean,
copy the `X-RateLimit-*` headers — and on rejection writes
`application/problem+json` (the same `type/title/status/detail/instance` +
`errorCode` + `correlationId` shape the services' exception handlers return)
and `Retry-After`. `Retry-After` is `ceil(requestedTokens / replenishRate)`
seconds, which is 1 here. The filter runs at `HIGHEST_PRECEDENCE + 20`: after
the correlation-id and user-context filters, before any body-rewriting filter.

**Fix 6 — `pom.xml`.** Mockito 5 self-attaches its agent at runtime, which
JDK 21 warns about (JEP 451) and a future JDK will refuse. Mockito's
documented fix is used: `maven-dependency-plugin:properties` exposes
mockito-core's jar path, and surefire passes it as `-javaagent`. `-Xshare:off`
also silences the CDS warning a `-javaagent` triggers. It is a profile activated
by `src/test/java` existing, because spendwise-common has no tests and no
mockito-core. A plain plugin there would leave the property unresolved and
break the forked JVM. Surefire 3.5.2 and dependency-plugin 3.8.1 are now
pinned; a BOM import manages dependency versions, not plugin versions.

### Validating Milestone 17

**This milestone was not compiled or run where it was written.** The build
sandbox has no access to Maven Central or Docker Hub and no Docker daemon. What
was verified there: every YAML/XML/JSON file parses; `docker compose config`
passes, with and without the `tools` profile; there are no duplicate host
ports; and the database script, all five transaction-service migrations, and
the `SKIP LOCKED` claim query ran against a real Postgres 16. Every
third-party API used was checked against that library's source at the exact
version in use (Spring Cloud Gateway 4.2.0, Spring Boot 3.4.1, spring-kafka
3.3.1, Apache Kafka 3.9.0). Step 0 is therefore the first real compile and
start-up of this code.

**Step 0 — build and start (Git Bash).**

```bash
cd /d/spendwise
./mvnw clean test-compile
./mvnw -pl api-gateway -am test
docker compose up -d --build
docker compose ps -a
docker compose --profile tools up -d kafka-ui
```

- `test-compile` must end in `BUILD SUCCESS` for all ten modules.
- The api-gateway test run (the one module with unit tests) must not print
  `Mockito is currently self-attaching` (fix 6).
- In `docker compose ps -a`: `spendwise-postgres-db-init` shows `Exited (0)`
  (fix 2); the three `spendwise-kafka-N` containers and every service show
  `healthy`.
- `docker compose logs transaction-service | grep "Topic transaction-events ready"`
  shows the topic was provisioned.

**Steps 1–7 — Postman.** Import `postman/SpendWise.postman_collection.json` and
run the `Milestone 17 - Test Plan` folder top to bottom. Every request asserts
its own expected result in its Tests tab. A folder that needs a container
stopped first says so in its description; those are the only terminal steps.

| Folder | What it proves | Expected |
| --- | --- | --- |
| 0. Setup | two users, a profile, a category | all `201` |
| 1. Outbox to Kafka | outbox row → Kafka on broker ack | `201`; in Kafka UI, one record keyed by the transaction id, headers `eventType`, `outboxEventId`, `X-Correlation-ID` (= the one Postman sent), `traceparent`; topic shows 3 partitions, RF 3 |
| 2. Kafka outage | writes survive a full Kafka outage | `201` with all brokers stopped; record appears after they restart; with only `kafka-3` stopped, published immediately |
| 3. gRPC deadline / 503 | fix 1, the new analytics route, `dns:///` re-resolution | `200`; budget-service stopped → `503` `DOWNSTREAM_SERVICE_UNAVAILABLE` in < 3 s; restarted and then paused → `503` at ~2 s |
| 4. Rate limit, user A (Runner, 60×) | fixes 3 + 5 | `200`s then `429`s; each `429` is `application/problem+json`, `Retry-After: 1`, `errorCode RATE_LIMIT_EXCEEDED`, `correlationId` set |
| 5. Rate limit, user B | buckets are per principal | `200` while user A is still throttled |
| 6. Anonymous by X-Forwarded-For (Runner, 60×) | IP fallback | `400`s then `429`s for `203.0.113.10`; a different value is a fresh bucket |
| 7. Redis down | fix 4 | `200` in < 1 s with `X-RateLimit-Remaining: -1` (fail-open marker) |

To see `processed_at` itself rather than its effect in Kafka UI, connect any
Postgres client (DBeaver, pgAdmin) to `localhost:5432`, database
`transaction_db`, `spendwise`/`spendwise`, and open `outbox_events`. Rows from
folder 1 have `processed_at` and `correlation_id` set. During folder 2's
outage, the new row's `processed_at` stays empty until the brokers return.

**Milestone 18 complete** — Mass Scale Ingestion (Consumer Groups &
Rebalancing). Scoped to the roadmap's sentence: independent `@KafkaListener`s
in notification-service and analytics-service, each with its own `group.id`,
consuming the same `transaction-events` topic in parallel for different
purposes. Plus the chaos lab: four instances of one consumer group against
three partitions, then kill an active one. No fixes were raised validating
Milestone 17. Deliberately out of scope here:

- consumer-side idempotency — Milestone 19;
- dead-letter routing — Milestone 20;
- analytics projections — Milestone 23;
- budget-service consuming the topic — Milestone 24.

**Consumer settings, once for every service — `config-repo/application.yml`.**

| Setting | Why |
| --- | --- |
| `group-id: ${spring.application.name}` | Each service is its own group automatically, so two services on one topic each get every record, while replicas of one service share them. |
| `auto-offset-reset: earliest` | A new group starts from the beginning of the topic, so Milestone 17's already-published events are consumed. Milestone 23's replay depends on this. |
| `CooperativeStickyAssignor` | Incremental rebalancing: only partitions that must move are revoked, and everyone else keeps consuming. The eager default revokes everything from everyone. |
| `session.timeout.ms: 10000`, `heartbeat.interval.ms: 3000` | The group notices a killed member in 10 s instead of 45 s. |
| `ack-mode: record` | The offset is committed after each record, so a crash redelivers at most the record in flight. |
| `concurrency: 1` | One consumer thread per instance, so instances and group members are the same count in the chaos lab. |
| `observation-enabled: true` | The listener continues the producer's trace from `traceparent`. |

`bootstrap-servers` moved here from transaction-service.yml. The topic name
for listeners is `spendwise.kafka.topics.transaction-events`.

**`TransactionAlertListener`** (new, `notification-service/.../messaging`)
turns each `TransactionCreatedEvent` into a user alert. Dispatch is
simulated: a `[SIMULATED PUSH]` log line. Real channels and the idempotency
store that guards them come later.
- It skips any other event type on the topic, so Milestone 21's reversal
  events can't be mistaken for new transactions.
- An unparseable payload is rethrown. The default error handler retries it,
  then logs and skips it; Milestone 20 sends it to a dead-letter topic instead.
- `idIsGroup = false` matters: by default Spring Kafka uses the listener's
  `id` as its group id, which would have silently replaced
  `notification-service` with `transaction-alerts`.

**`TransactionIngestionListener`** (new, `analytics-service/.../messaging`)
is the entry point of the read side. It consumes the same records in its own
group (`analytics-service`) and logs each event with the dimensions the read
models will key on: user, month, category, base amount. Storing projections
is Milestone 23. It runs on a listener container's own consumer thread,
never on Netty's event loop, so plain blocking code is fine in this WebFlux
service.

**Two `TransactionCreatedEvent` records** (new, one per consumer service)
follow the platform's per-consumer contract rule, and deliberately differ:
- each declares only the fields it uses — analytics keeps `categoryId` and
  `baseCurrencyAmount`, notification doesn't;
- each is a tolerant reader: `@JsonIgnoreProperties(ignoreUnknown = true)`,
  and `type` is a `String` rather than a copy of the producer's enum, so
  producer-side additions can't break either consumer.

notification-service also gets tracing dependencies, skipped at Milestone 3
because it had no entry point to trace until now.

**Shared consumer infrastructure — `spendwise-common/.../messaging`.**
Spring Boot applies a single `RecordInterceptor<Object,Object>` bean and a
single `ConsumerAwareRebalanceListener` bean to its listener container
factory, checked against Boot 3.4.1's `KafkaAnnotationDrivenConfiguration`.
So these are written once, like `CorrelationIdFilter`:

- **`CorrelationIdRecordInterceptor`** — puts the record's
  `X-Correlation-ID` header into the MDC before the listener runs and
  removes it in `afterRecord`, on the same consumer thread. One search on a
  correlation id now finds the HTTP request, the outbox publish, and both
  consumers. It also counts `spendwise.kafka.records.consumed`, tagged by
  group, topic, partition, event type and outcome; the partition tag makes
  each instance's share visible through `/actuator/metrics`.
- **`PartitionAssignmentLoggingListener`** — logs partitions newly
  assigned, revoked, and lost (the unclean case, after a missed session
  timeout), so a rebalance can be read in `docker compose logs`.
- **`KafkaAssignmentsEndpoint`** — `GET /actuator/kafkaassignments`: the
  partitions this instance owns right now, and its container hostname. This
  is what lets Postman check the chaos lab directly. Exposed only on
  notification-service and analytics-service.
- **`EventHeaders`** — the envelope header names (`outboxEventId`,
  `eventType`, `aggregateType`), now used by `OutboxPublisher` too, so
  producer and consumers can't drift on a name. Payload types stay
  per-service.
- **`SpendwiseKafkaConsumerAutoConfiguration`** wires all of this. It is
  conditional on spring-kafka being on the classpath (declared `optional` in
  this module) and ordered before `KafkaAutoConfiguration`. The meter
  registry is resolved through `ObjectProvider` rather than a bean
  condition, which could silently drop the interceptor depending on
  auto-configuration order.

**`docker-compose.yml`.**
- notification-service is now scalable: no `container_name` (Compose names
  replicas `spendwise-notification-service-1..4`), and a host port *range*,
  `18085-18088`, so each replica is reachable from Postman.
- It is no longer published on 8085, because the range `8085-8088` would
  have overlapped analytics-service's 8086. The container port is unchanged,
  so Prometheus and in-network callers are unaffected.
- Its memory limit drops to 384 MB so four replicas fit.
- Both consumers get `KAFKA_BOOTSTRAP_SERVERS` and
  `depends_on: service_started` on the brokers. A consumer that starts
  before the cluster just keeps retrying.

**Q58: What are Kafka Consumer Group mechanics, and how do partition
allocations scale?** A consumer group is a set of consumers sharing one
`group.id`. The broker-side group coordinator assigns each partition of the
subscribed topics to exactly one member of the group, and tracks the group's
committed offset per partition. That gives two rules:

- **Within a group, records are divided.** Each partition has one owner, so
  per-partition order is preserved and work is spread out.
- **Across groups, records are copied.** Each group has its own offsets, so
  notification-service and analytics-service both read every record, at
  their own pace, without affecting each other.

Scaling follows from the partition count. Parallelism inside a group is
capped at the number of partitions: with 3 partitions, members 1–3 each own
partitions, and a 4th member owns nothing. It isn't useless, though — it is
a hot standby that takes over the moment an owner disappears, which is the
chaos lab.

Membership changes trigger a rebalance:
- **join** — a new instance;
- **leave** — clean shutdown, immediate;
- **missed heartbeats** — a crash, detected after `session.timeout.ms`.

With the cooperative-sticky assignor, a rebalance is incremental: only the
moved partitions pause.

To scale consumption past one partition per consumer, add partitions. But
changing the partition count changes which partition a key hashes to, which
breaks per-key ordering for in-flight keys. So partitions are sized for
expected peak parallelism up front. Consumer lag per partition (Kafka UI →
Consumers) is the signal to scale.

### Validating Milestone 18

An independent review of the diff, needed because none of this could be
compiled where it was written, found four issues, fixed before handover:

- **Port drift.** Docker hands out ports from a range round-robin, not
  lowest-free, so a recreated notification-service replica can land on
  18086–18088. The Postman requests that use `notificationServiceUrl` now
  find the live port themselves; `docker compose port notification-service 8085`
  shows it from the terminal. Scaling with a port range needs Docker Compose
  2.17.3 or newer (current Docker Desktop is).
- **JVM headroom.** At a 384 MB limit, the image's `MaxRAMPercentage=75`
  left about 96 MB outside the heap. Compose now sets 60% for this service.
- **Prometheus.** A static `notification-service:8085` target scrapes one
  replica per scrape, chosen at random. It is now a `dns_sd_configs` job, so
  each replica is its own target.
- **Endpoint race.** `getAssignedPartitions()` is a live view that the
  consumer thread mutates during a rebalance. The endpoint now copies it with
  a retry instead of risking a `ConcurrentModificationException` (a 500).

Like Milestone 17, this was written in a sandbox with no Maven Central,
Docker Hub or Docker daemon, so step 0 is its first real build. Verified
there: all YAML/XML/JSON parses, `docker compose config` passes, and there
are no host-port overlaps (port ranges expanded). Every new spring-kafka,
kafka-clients and Boot API was checked against source at the versions in use.

**Step 0 — build and start (Git Bash).**
```bash
cd /d/spendwise
./mvnw clean test-compile
docker compose up -d --build
docker compose --profile tools up -d kafka-ui
docker compose logs notification-service | grep "Partitions newly assigned"
docker compose logs analytics-service | grep "Partitions newly assigned"
```
Each `grep` should show the three `transaction-events` partitions assigned
to that service's single instance.

**Steps 1–3 — Postman, folder `Milestone 18 - Test Plan`.**
`notificationServiceUrl` is now `http://localhost:18085`.

| Folder | What it proves | Expected |
| --- | --- | --- |
| 0. Setup | token, profile, category | all `201` |
| 1a → 1b (Runner, 10×) → 1c | two groups, every record each | both services' `spendwise.kafka.records.consumed` rise by at least the number created; Kafka UI → Consumers shows both groups at lag 0 |
| 2. One instance per group | single member owns everything | `notification-service` and `analytics-service` groups each own `transaction-events-0, -1, -2` |
| 3. Chaos lab | rebalance, idle standby, healing | after scaling to 4: every partition owned once, exactly one replica idle; after killing an owner: 3 replicas, each owning one — the idle replica took over |

Folder 3's description has the three Git Bash steps: scale to 4, kill an
owner, scale back to 1. During the lab, `docker compose logs -f
notification-service` shows each rebalance: `Partitions newly assigned …`,
`Partitions revoked …`, and, after `docker kill`, the survivors picking up
the orphaned partition about 10 s later.

Memory: four notification replicas at 384 MB each, on top of the full stack
and Kafka UI, can exceed a 5.6 GB Docker VM. If containers restart with exit
code 137 during the lab, raise the WSL 2 VM's memory: `%UserProfile%\.wslconfig`
with `[wsl2]` / `memory=8GB`, then `wsl --shutdown` and restart Docker Desktop.

Next: Milestone 19 — Distributed Idempotency Layers.

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
