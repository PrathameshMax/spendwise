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
# service registry: http://localhost:8761 (Eureka dashboard — registered instances)
# traces:      http://localhost:9411
# metrics:     http://localhost:9090 (Prometheus)
# dashboards:  http://localhost:3000 (Grafana, admin / spendwise)
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
docker compose up -d zipkin postgres prometheus grafana
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
`SpendWiseException` already uses — no new handler code needed. Getting this
right required one correctness detail easy to miss: both the retry policy and
the new circuit breaker policy exclude `feign.FeignException$NotFound` via
their own `ignore-exceptions`. Without that, a perfectly healthy user-service
correctly answering "no such user" would both count as a circuit-breaker
failure *and* get misrouted through the fallback — turning a correct 404 into
a false 503. A business answer is not a partial failure (Q47); only a
genuinely broken or overloaded downstream should ever reach the fallback.

🔥 **Chaos Lab — try it yourself:**
1. In `user-service`'s `UserController.getById`, temporarily replace the
   method body with `throw new RuntimeException("Simulated failure");` and
   rebuild: `docker compose up -d --build user-service`.
2. Fire a burst of concurrent requests at transaction-service through the
   gateway — at least 5-10, to clear `minimum-number-of-calls`:
   ```bash
   for i in {1..15}; do
     curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/transactions \
       -H "Content-Type: application/json" \
       -d "{\"userId\":\"<existing-id>\",\"categoryId\":\"<existing-id>\",\"amount\":5,\"type\":\"EXPENSE\",\"description\":\"chaos\",\"transactionDate\":\"2026-10-01\"}"
   done
   ```
3. Watch `docker compose logs transaction-service --since 2m | grep -i "CircuitBreaker\|fell back"` — you'll see the circuit breaker's own state-transition log line (`CLOSED` → `OPEN`) around the point `failure-rate-threshold` is crossed, followed by `userServiceLookup` logging the fallback firing.
4. Notice the later requests in the burst return `503` essentially instantly — no more waiting through 3 retry attempts each, since `CallNotPermittedException` short-circuits before any network call is even attempted. That's the thread-pool protection the roadmap calls out: once OPEN, transaction-service stops spending threads/connections on a downstream it already knows is failing.
5. Stop sending traffic, wait past `wait-duration-in-open-state` (10s), then send one more request — the circuit moves to `HALF_OPEN` and lets it through as a trial call.
6. Revert `UserController.getById` and rebuild user-service again before moving on.

Next: Milestone 13 — The Full Resilience4j Suite (Bulkhead & TimeLimiter).

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
