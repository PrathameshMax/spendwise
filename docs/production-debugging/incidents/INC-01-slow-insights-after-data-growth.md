# INC-01: Home screen insights exhaust transaction-service's connection pool after data growth

| | |
|---|---|
| Severity | P2 at report, raised to **P1** (transaction history affected) |
| Service | transaction-service (Postgres `transaction_db`) |
| Customer impact | Home screen insights, statements and transaction history slow or failing (p95 up to 30 s+, 5xx up to 64%) |
| Impact window | ~14:05 to ~23:00 IST, 27 Oct 2026 (monitoring gap 14:40 to 15:45, see below) |
| Mitigation | Rollback of release 2026.10.27, ~23:00 |
| Permanent fix | `3881db9`, deployed ~01:40, 28 Oct; verified ~02:00 |
| Environment | SpendWise production debugging lab (reproduced incident, not a real outage) |

## Summary

Release 2026.10.27 added a "This month" insights card to the mobile home screen,
backed by two native SQL queries in `InsightsRepository`. Both queries wrapped
indexed columns in functions (`CAST(user_id AS varchar)`,
`to_char(transaction_date, 'YYYY-MM')`), so Postgres could not use the existing
indexes and scanned the whole `transactions` table on every call.

On the small pre-release table this cost milliseconds. After the weekend
historical migration grew the table to 1.45 million rows (322 MB), each call took
4 to 7 seconds, and one home-screen request held a database connection for about
11 seconds. At normal traffic (~1.5 home-screen requests/s) that needs ~16
connections continuously; the pool has 10. The pool stayed full, and every other
endpoint in transaction-service queued behind the insights queries.

## Timeline (IST)

| Time | Event |
|---|---|
| 26 Oct 18:03 | Release 2026.10.27 (Spend Insights on the home screen) built |
| 27 Oct 13:55 | Release live with normal traffic; baseline p95 < 500 ms |
| ~14:05 | Historical data migration starts; latency on transaction-service rises |
| 14:19 | Migration fails at 1,454,595 of ~2.88M rows: `Could not open JPA EntityManager for transaction` (Hikari timeout, `total=10, active=10, waiting=103`) |
| 14:05 to 14:40 | p95/p99 pinned at 30 s (histogram ceiling), 5xx up to 64%, Hikari 10/10, ~100 threads waiting |
| 14:40 to 15:45 | No metrics: monitoring host asleep (lab laptop). Not a recovery |
| ~16:05 | Degradation returns with normal traffic only, migration no longer running |
| 16:20 | Three alerts confirmed firing: `HighLatencyP95`, `DbConnectionPoolNearlyFull` (warning), `DbConnectionWaits` (critical). The Grafana "Firing alerts" panel had a `severity=critical` filter and had shown "No alerts firing" |
| 16:36 | Thread dump: all 10 connections held by `InsightsService.monthly` (6 in `topUpiMerchants`, 4 in `totalsByCategory`), RUNNABLE in `PGStream`. 106 threads waiting: 59 `TransactionService.list` (statements), 44 more insights calls, 2 health checks, 1 outbox publisher. Raised to P1 |
| 18:35 | DBA plans with actual timings: Parallel Seq Scan of 1,454,679 rows to return 49 / 43 rows; 6.6 s and 4.3 s. Indexes on `user_id` and `transaction_date` exist but are unused. No lock waits |
| ~19:20 | Root cause identified: non-sargable predicates |
| ~23:00 | Mitigation: release rolled back (no DB migrations in the release). Latency, pool and 5xx recover |
| 28 Oct ~01:40 | Release restored with the permanent fix (`3881db9`) under an emergency change |
| ~02:00 | Fix verified: same results, Index Scan, 5.8 ms / 0.29 ms; p95 31 ms; alerts clear |
| ~02:05 | Verification finds a regression: malformed `userId` returns 500 instead of 400 |
| 28 Oct 19:03 | Follow-up deployed: malformed `userId` rejected with 400 |

## Root cause

```sql
WHERE CAST(t.user_id AS varchar) = :userId
  AND to_char(t.transaction_date, 'YYYY-MM') = :month
```

A B-tree index stores the column's raw values in sorted order. Postgres can search
it only for predicates on that raw value (`user_id = ?`, a date range). A predicate
on a function of the column (a non-sargable predicate) has to be evaluated on every
row, so both queries fell back to a sequential scan of the full table. The
statement endpoint, which compares `user_id` directly, kept using
`idx_transactions_user_id` on the same table: the problem was the shape of the
predicate, not a missing index.

Cost grew with table size: invisible on the pre-release table, seconds per call at
1.45M rows.

### Why the pool, not the database, was the visible symptom

Connections needed ≈ request rate × time each request holds a connection.
1.5 req/s × ~11 s ≈ 16 connections, against a pool of 10. A larger pool would have
run more concurrent full-table scans on the same Postgres and made each one slower;
it was rejected as a fix.

## Contributing factors

1. **Query shape not reviewed against indexes.** Native SQL was merged without an
   execution plan check on production-like volume.
2. **Tests ran on tiny data.** The repository test proved correctness, not
   performance; a full scan of a few rows is fast.
3. **Data growth was decoupled from the release.** The release looked healthy for
   days; the migration exposed it.
4. **No statement timeout.** Slow queries held connections for their full duration;
   the only limit was Hikari's 30 s wait for *new* connections.
5. **One endpoint could starve the whole service.** All endpoints share one pool, so
   the home-screen card took down statements and history.
6. **Alert visibility.** Alerts fired, but the dashboard's `severity=critical` filter
   hid two of the three.

## Resolution

**Mitigation:** rolled back release 2026.10.27 (checked first: no Flyway migrations
in the release). The insights card was unavailable until the fix shipped.

**Permanent fix (`3881db9`):** predicates on raw columns, typed parameters.

```sql
WHERE t.user_id = :userId                      -- UUID parameter
  AND t.transaction_date >= :monthStart        -- LocalDate, YearMonth.atDay(1)
  AND t.transaction_date <  :nextMonthStart    -- half-open range
```

**Follow-up:** `userId` request parameter typed as `UUID`, so malformed input is
rejected by Spring with 400 before reaching the service.

## Verification

| | Before | After |
|---|---|---|
| Plan, totalsByCategory | Parallel Seq Scan, 1,454,679 rows read, 6,632 ms | Index Scan `idx_transactions_user_id`, 678 rows read, 5.8 ms |
| Plan, topUpiMerchants | Parallel Seq Scan, 1,454,679 rows read, 4,276 ms | Index Scan `idx_transactions_user_id`, 678 rows read, 0.29 ms |
| Rows returned | 49 / 43 | 49 / 43 (identical results) |
| transaction-service p95 | ~30 s | 31 ms |
| Insights p99 | ≥ 30 s | 47 ms |
| Hikari | 10/10 active, ~100 waiting | low, 0 waiting |
| Alerts | 3 firing | none |
| Malformed `userId` | 200 (before fix), 500 (after fix) | 400 |

Evidence: `dba-output.txt` (before), `dba-after.txt` (after), Grafana and Prometheus
screenshots, thread dump `2026-10-27 105520.txt`.

## What went well

- The thread dump separated holders (10, all one code path) from waiters (106),
  which named the culprit and the blast radius in one step.
- A written prediction before the execution plan (Seq Scan, ~1.45M read, ~40
  returned) turned the plan into a test of the hypothesis.
- Mitigation (rollback) was separated from the fix and checked for DB migrations
  first.
- Post-deploy verification found a regression the fix introduced.

## What went less well

- The first hypothesis (pool too small) treated the symptom; the evidence showed
  connections held for seconds, not too few connections.
- The dashboard filter hid firing alerts for the first part of the investigation.
- No web-layer tests existed, so the 500-for-bad-input regression reached
  production.

## Action items

| # | Action | Type | Priority |
|---|---|---|---|
| 1 | Malformed `userId` → 400 | Fix | Done |
| 2 | Review checklist: no functions on indexed columns in WHERE; EXPLAIN on production-like volume for new queries | Prevention | High |
| 3 | Statement timeout for transaction-service (e.g. `SET statement_timeout` via Hikari `connection-init-sql`), sized above the slowest legitimate query | Resilience | High |
| 4 | Web-layer (`@WebMvcTest`) tests for transaction-service, including invalid-input cases | Testing | Medium |
| 5 | Composite index `(user_id, transaction_date)` (reads ~49 rows instead of ~678); via the normal schema-change process | Optimisation | Low |
| 6 | Remove the `severity=critical` filter from the saved dashboard link | Monitoring | Medium |
| 7 | Upgrade Testcontainers to ≥ 1.21.4 (Docker Engine 29 API compatibility) | Tooling | Medium |
| 8 | Consider isolating heavy read endpoints (separate pool or bulkhead) so one feature cannot starve core endpoints | Design | Low |

## Interview note

This incident was reproduced in a personal lab built to practise production
debugging. Describe it as such.
