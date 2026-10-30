# INC-05: Slow partner API exhausts transaction-service's request threads

| | |
|---|---|
| Severity | **P1**: statements, transaction history and home-screen insights unavailable |
| Service | transaction-service; dependency: MerchantDirectory (third-party partner API) |
| Impact window | ~02:33 to ~04:38 IST, 29 Oct 2026 |
| Detection | `ServiceDown` (critical) for transaction-service at 02:38, while the container was running |
| Mitigation | Rollback of release 2026.10.29, ~04:38 |
| Permanent fix | CR-1: instrumented client with timeouts, circuit breaker, bulkhead, fallback for any failure. Verified 29 Oct evening against the partner degraded and recovered |
| Environment | SpendWise production debugging lab (reproduced incident, not a real outage) |

## Summary

Release 2026.10.29 added merchant names, categories and logos to statement
entries, fetched from the MerchantDirectory partner API once per statement page.
The client was created with `new RestTemplate()`, which has **no connect or read
timeout**. When MerchantDirectory started responding only after minutes, every
statement request waited for it indefinitely.

transaction-service serves all endpoints from one pool of **200 Tomcat request
threads**. Statement traffic (~2 req/s) filled the pool in about 3 minutes. From
then on every request queued behind it, including home-screen insights (which
never call the partner), the health check and the Prometheus scrape. The
service looked down while the process was running and the database was idle.

## Timeline (IST, 29 Oct 2026)

| Time | Event |
|---|---|
| ~02:10 | Release 2026.10.29 live; baseline: p95 ~130 ms, Tomcat busy ≤ 24, 0% 5xx |
| ~02:33 | Tomcat busy threads for transaction-service start climbing (MerchantDirectory degraded) |
| ~02:36 | Pool at **200/200** |
| 02:38 | `ServiceDown` firing: Prometheus can no longer scrape transaction-service |
| 02:40 | Mobile team: statement and home screens spinning, then failing |
| 02:54 | Grafana triage: only transaction-service saturated; Hikari idle (0 active, 0 waiting) |
| ~03:10 | `/actuator/threaddump` unreachable (same thread pool); dump requested from the platform team |
| ~03:20 | 3 dumps via `kill -3`, 10 s apart: **200 of 200** request threads in `MerchantDirectoryClient.lookup` → `Net.poll` / `SocketDispatcher.read0` (waiting for the partner's response), identical in all three |
| ~03:50 | Root cause: no timeouts on the partner client; shared pool as amplifier |
| ~04:38 | Mitigation: release rolled back (no DB migrations). Tomcat busy 200 → 6, `ServiceDown` cleared, latency back to baseline by ~04:55 |
| 05:00 | Stakeholder update; vendor asked to confirm their incident |
| 18:55–19:10 | SIT, step A (timeouts only), partner degraded: threads stay low, but statements take ~1.5 s and **5–8% return 500** (see "Defect found in testing") |
| ~21:12 | SIT, step B (circuit breaker, bulkhead, fallback for any failure): circuit opens; statement p95 **36.6 ms**, 5xx **0%** |
| ~21:30 | Vendor recovers; half-open trial calls succeed; circuit **closes**; merchant details return automatically |

## Root cause

**Trigger:** MerchantDirectory became extremely slow (responses after minutes).

**Missing protection:** `MerchantDirectoryClient` used `new RestTemplate()`, whose
`SimpleClientHttpRequestFactory` has no connect or read timeout, so each call
could hold a request thread indefinitely. The client caught `RestClientException`
for graceful degradation, but without a timeout no exception ever arrived:
error handling without a timeout protects nothing.

**Amplifier:** one shared pool of 200 Tomcat threads for every endpoint, and
actuator on the same port. Threads needed ≈ request rate × seconds held
(~2 req/s × minutes), far beyond 200, so an optional, cosmetic feature took down
the whole service, its health check and its monitoring.

## Investigation notes

- **The database was ruled out first.** The Hikari pool was idle; the tooltip
  "10" was the pool *size* (`max`), not connections in use. This separated
  INC-05 from INC-01 (pool exhaustion by slow queries).
- **"No 500s" was not "no errors".** Hanging requests never complete, so they are
  never counted; after 02:38 there was no scrape data at all.
- **`RUNNABLE` in the dump was not CPU.** Java reports threads blocked in a native
  socket read as RUNNABLE.
- **First hypothesis "increase the pool size" was rejected** with the incident's
  own numbers: 200 threads filled in ~3 minutes, so 400 would fill in ~3 more.

## Resolution (CR-1)

| Layer | Change | Proven by |
|---|---|---|
| Instrumented client | `RestClient` from Spring Boot's `RestClient.Builder` (not `@LoadBalanced`) | Partner calls visible as `http.client.requests` metrics and Zipkin `http post` spans |
| Timeouts | Connect 500 ms, read 1.5 s (`JdkClientHttpRequestFactory`), configurable | Step A: Tomcat busy ~3 instead of 200 |
| Circuit breaker `merchantDirectory` | Window 20 calls, min 10, open at 50% failures or 50% calls > 1 s, open 30 s, 3 half-open trials | Step B: open while partner degraded; closed by itself after recovery |
| Bulkhead `merchantDirectory` | Max 10 concurrent partner calls, no waiting | Caps partner use of request threads (healthy need ≈ 1) |
| Fallback | Any `Throwable` → statement without merchant details; unexpected failures logged with type | 5xx 0% in step B, including cancellations |

## Defect found in testing

With timeouts only (step A), **5–8% of statements returned 500**. Logs and the
client metric showed `java.util.concurrent.CancellationException` from the JDK
HTTP client when a request was cancelled on timeout. It is not a
`RestClientException`, so the `catch` did not cover it, and it reached the global
handler. Fixed in step B by making the circuit breaker's fallback handle any
failure of the optional call. Lesson: a fallback's coverage must be defined as
"any failure of this optional call", and tested with the dependency slow.

## Verification

| | Incident | Step A (timeouts) | Step B (CR-1) |
|---|---|---|---|
| Statement p95 | timeout / no data | ~1.76 s | **36.6 ms** (partner degraded) |
| Tomcat busy (transaction-service) | 200/200 | ~3–5 | **2** |
| 5xx | not measurable | 5–8% | **0%** |
| Partner calls/s | ~2 (all hanging) | ~2 (mostly timing out) | **~0.18** while open (half-open trials only) |
| `merchant` field | — | null | null while partner down; **filled** after recovery |
| Statement with healthy partner | — | — | 200 in 201 ms with merchant details |

Evidence: Grafana and Prometheus screenshots, `threaddumps.txt` (3 dumps),
Zipkin traces, transaction-service ERROR logs (`CancellationException`).

## Contributing factors

1. A new external dependency was added without timeouts, and code review did not
   check the client's configuration.
2. Graceful degradation was tested only with fast failures, not with a slow or
   hanging dependency.
3. One shared request-thread pool for all endpoints; no isolation for an optional
   call.
4. Actuator on the application port: health checks, metrics and the thread-dump
   endpoint failed together with the application ("monitoring went blind").
5. The partner client was not instrumented, so partner latency was invisible
   until a thread dump.

## What went well

- The baseline taken before the incident made the deviation obvious (Tomcat busy
  24 → 200).
- The thread dump, taken via `kill -3` when the HTTP endpoint was unavailable,
  named the exact line of code.
- Mitigation (rollback) came before the fix and was checked for DB migrations.
- Every resilience pattern was proven by an experiment against a degraded and
  then recovered partner, which also exposed the `CancellationException` defect.

## What went less well

- Initial readings mixed up pool size with pool usage, and treated "no recorded
  errors" as "no errors".
- The first hypothesis proposed a bigger thread pool.
- The vendor request initially had the wrong start time (traffic start, not
  degradation start).

## Action items

| # | Action | Type | Status |
|---|---|---|---|
| 1 | Timeouts, circuit breaker, bulkhead, fallback for any failure | Fix | Done (CR-1) |
| 2 | Cache-aside for merchant details with TTL (Redis) | Resilience | CR-2 |
| 3 | Kill switch `merchant-enrichment.enabled`, changeable at runtime | Operability | CR-2 |
| 4 | Actuator on a separate management port; health check and scrape target updated | Operability | CR-3 |
| 5 | Review checklist: every outbound call has timeouts, a bounded concurrency and a fallback | Prevention | Open |
| 6 | Restart policy `unless-stopped` for infrastructure containers (postgres, redis, prometheus, grafana, zipkin) | Lab platform | Open |
| 7 | Test resilience with the dependency slow, down and erroring, not just failing fast | Testing | Open |

## Concepts to understand with evidence (follow-up sessions)

- **Health-check design:** why the partner must not be part of transaction-service's
  liveness or readiness.
- **Why not retry here:** retrying a slow dependency multiplies waiting threads and
  the load on the vendor.

## Interview note

This incident was reproduced in a personal lab built to practise production
debugging. Describe it as such.

## Addendum: CR-2 (cache-aside, kill switch, health-check fix)

Implements action items 2 (cache-aside with TTL) and 3 (kill switch).

### Changes

- **Cache-aside in Redis.** One `MGET` per statement page; MerchantDirectory is
  called only for cache misses; fetched details are written back with a 24 h TTL.
  Keys: `merchant-directory:v1:<CODE>` (versioned prefix).
- **Fail-open cache.** Any Redis or serialization failure is a cache miss (read)
  or a skipped write. Redis connect and command timeouts are 200 ms, and Lettuce
  `REJECT_COMMANDS` makes a down Redis fail immediately instead of queueing.
- **Kill switch** `spendwise.merchant-directory.enrichment-enabled`, read per
  request, so a config change plus `POST /actuator/refresh` applies it without a
  restart.
- **Health check.** Redis excluded from transaction-service's health status: an
  optional, fail-open dependency must not mark a working service DOWN.
- **Config drift fixed.** Since CR-1 the config repo served connect 1500 ms /
  read 500 ms, the reverse of the change request (connect 500 / read 1500).
  Found by querying the config server; corrected in CR-2.

### Defects found during CR-2 testing

1. **Cache ignored, partner called twice per page.** Caught by three failing unit
   tests (cache hit still fetched, nothing written back, two partner calls for a
   fully cached page). Cause: the old direct partner call was left in the
   enricher next to the new cache logic. Fixed by replacing the class.
2. **Key prefix missing `:`.** `SCAN merchant-directory:v1:*` returned 0 while the
   cache held 52 keys (`merchant-directory:v1ZOMATO`). The cache worked, but the
   namespace was broken. Fixed; the corrected prefix acted as a whole-cache
   invalidation, the old keys expiring through their TTL.

### Evidence

| Experiment | Result |
|---|---|
| Partner healthy, cache warm | ~2 statements/s, ~0.09 partner calls/s (~95% served from cache); statement 200 in 35 ms with merchant details; 5xx 0% |
| E1: partner down, cache warm | [fill: logos shown for which merchants; partner calls/s; statement p95; circuit state] |
| E2: Redis down | [fill: statement status and time; health endpoint before the fix (status/HTTP code) and after the fix] |
| E3: kill switch off then on | [fill: refresh response (changed keys); `mget` calls stopped; `merchant` null; no restart] |

### Concepts demonstrated with evidence

- **Cache-aside with TTL:** the partner is called for misses only; during a
  partner outage cached merchants keep their details.
- **Versioned keys as invalidation:** changing the prefix invalidates the whole
  cache safely; old entries expire through their TTL.
- **Health-check design:** an optional dependency (cache) must not take part in a
  service's health status, or probes restart a service that is working.
- **Kill switch:** operations can turn the feature off at runtime, without a deploy
  or restart.
- **Config drift:** what the config server serves is the source of truth for
  what runs. Verify it after every config change.

### Remaining action items

| # | Action | Status |
|---|---|---|
| 4 | Actuator on a separate management port | CR-3 |
| 5 | Review checklist for outbound calls (timeouts, bounded concurrency, fallback) | Open |
| 6 | Restart policy `unless-stopped` for infrastructure containers | Open |
| 7 | Resilience tests with dependencies slow, down and erroring | Partly done (CR-1, CR-2 experiments) |
| 8 | Understand: why not retry here | Open |
