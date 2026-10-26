# SpendWise Production Debugging Lab

A hands-on lab for diagnosing realistic Java microservices incidents the way
they are handled in a bank: from alerts, logs, APM and dashboards, under
production access restrictions, with the incident process around it.

Every incident here is **reproduced in this lab**. When talking about this work,
describe it as a lab built to reproduce production failure patterns, not as an
incident from a previous employer.

## How an investigation runs

The investigation is interactive and the engineer makes the calls.

1. **Alert.** The incident starts the way monitoring or users would report it.
2. **First step.** The engineer decides where to look first: which dashboard,
   which Kibana/Splunk search, which AppDynamics view. When stuck: a hint, not the answer.
3. **Evidence.** The engineer reports what they see (screenshots or pasted output).
4. **Hypothesis.** The engineer states a hypothesis; it gets challenged: what does
   this evidence prove, what does it not prove, what else could it be?
5. **Restricted actions.** Anything the platform team or a DBA would do is
   requested as on a bridge call, approved, and only then executed in that role.
6. **Mitigation.** The engineer decides, as with an incident manager: restart,
   rollback, scale, or wait.
7. **Fix.** The engineer writes the fix and deploys it through a change request.
8. **Recovery and RCA.** Recovery is verified on the same dashboards, then the RCA
   and the interview explanation are written.

The root cause is revealed only after a genuine attempt, or once found, to explain the theory.

### Production access model

| The engineer on support uses | Only through a request (platform team / DBA) |
|---|---|
| Kibana, Splunk, AppDynamics, Grafana, Prometheus alerts, Zipkin | Restart or scale a service |
| Postman: business APIs and Actuator endpoints, including `GET /actuator/threaddump` on a service's own port | Heap dump, or `jcmd`/`jstack` from inside a container |
| Reading code and configuration in the repository | Database queries, locks, explain plans |
| | Deploying a fix (change request + approval) |

Locally, `docker` and `psql` play the role of the platform team's and DBA's
tools. They are not used during an investigation unless the request was made.

## Lab components

| Component | Where | Notes |
|---|---|---|
| Production mode | `docker-compose.lab.yml` | `prod,lab` profiles (root WARN, com.spendwise INFO), ECS JSON logs, compose labels on every container log |
| Lab metrics | `config-repo/application-lab.yml` | Latency histograms (p95/p99), Tomcat thread metrics, `threaddump` endpoint |
| Test data | `POST/GET/DELETE /lab/seed` on transaction-service (8083) | `lab` profile only; realistic Indian retail-banking ledger; lab ids start with `1ab5eed0` and are removed without touching real data |
| Dashboard | Grafana http://localhost:3000, "SpendWise — Production Lab" | Firing alerts, golden signals, HikariCP pool, threads, heap/GC/CPU, Kafka lag |
| Alerts | `infra/prometheus/alerts-lab.yml`, http://localhost:9090/alerts | 10 symptom-based rules (latency, pool, threads, CPU, GC, heap, lag, DLT, service down) |
| Central logging (Kibana) | `infra/elk/` | Fluent Bit -> Elasticsearch -> Kibana http://localhost:5601, data view "SpendWise logs" |
| Central logging (Splunk) | `infra/splunk/` | Same Fluent Bit pipeline -> Splunk HEC -> index `spendwise`, http://localhost:8000 (admin / SpendWiseLab#2026) |
| Shared log pipeline | `infra/fluent-bit/pipeline.conf` | One reading/filtering/field-naming config for both platforms |
| APM (AppDynamics) | `docker-compose.appd.yml`, `infra/appdynamics/` | SaaS trial controller; agent on api-gateway, transaction-service, notification-service |
| Traffic | `infra/traffic/mobile-app.js` (k6), `lab.sh traffic-start/stop/status` | Steady mobile-app traffic for lab users: home screen and statement requests at a constant arrival rate; tunable with `HOME_SCREEN_PER_MIN`, `STATEMENT_PER_MIN`, `LAB_USERS` |
| Requests | `postman/SpendWise.postman_collection.json` | Folder "Production Debugging Lab" |
| Start / stop | `scripts/lab.sh` | `start`, `stop`, `status`, `traffic-*`, `kibana-*`, `splunk-*` |

Run one log platform at a time on a 16 GB laptop: the app stack uses about
7 GB, Elasticsearch + Kibana about 2.5 GB, Splunk about 2 GB. `lab.sh`
refuses to start one while the other runs.

### Searching logs: Kibana and Splunk side by side

Both platforms receive the same events with the same field names (dots in
ECS names become underscores: `log_level`, `service_name`, `error_type`).

| Question | Kibana (KQL, Discover) | Splunk (SPL, Search & Reporting) |
|---|---|---|
| One request across every service | `correlationId : "lab-1729600000000"` | `index=spendwise correlationId="lab-1729600000000"` |
| One trace (same as Zipkin) | `traceId : "<id>"` | `index=spendwise traceId="<id>"` |
| Errors from one service | `service_name : "transaction-service" and log_level : "ERROR"` | `index=spendwise service_name="transaction-service" log_level=ERROR` |
| Exceptions of a type | `error_type : *SQLTransientConnectionException*` | `index=spendwise error_type="*SQLTransientConnectionException*"` |
| Infrastructure containers (plain text) | `container_name : "postgres"` | `index=spendwise container_name=postgres` |
| Error count per service, per minute | Visualize: `log_level : "ERROR"`, break down by `service_name` | `index=spendwise log_level=ERROR \| timechart span=1m count by service_name` |
| Most frequent errors | Field statistics on `error_type` | `index=spendwise log_level=ERROR \| stats count by service_name, error_type \| sort -count` |

Search a field, never the bare value: `lab-1729600000000` typed alone is
split into `lab` and `1729600000000`, and `lab` matches every event.

### AppDynamics setup (once)

1. Sign up for the AppDynamics SaaS free trial (Splunk AppDynamics website).
   The trial clock starts at sign-up, so do it when you are ready to use it.
2. In the controller: gear icon > License > Account: note the account name
   and access key; the controller host is the host part of the controller URL.
3. Copy `infra/appdynamics/appd.env.example` to `infra/appdynamics/appd.env`
   and fill it in (git-ignored).
4. `./scripts/lab.sh start` prints "AppDynamics agent: ON". After 2-5 minutes
   of traffic, application **SpendWise-Lab** appears with three tiers.

## Incident catalogue

**A. Performance and JVM**

| ID | Incident | Typical trigger |
|---|---|---|
| INC-01 | Slow API after data growth | Missing index; table grows after a migration |
| INC-02 | Database connection pool exhausted | Slow queries hold connections |
| INC-03 | N+1 queries | Lazy association loaded in a loop |
| INC-04 | Database connection leak | Connection not returned on an exception path |
| INC-05 | Tomcat threads exhausted | Slow downstream, no timeout |
| INC-06 | Downstream service slow or down | Partner service degrades |
| INC-07 | CPU at 90-100% | Inefficient loop or regex on large input |
| INC-08 | Memory leak, then OutOfMemoryError | Unbounded static map or cache |
| INC-09 | Frequent or long GC pauses | Heap too small, large result sets in memory |
| INC-10 | Container OOMKilled (exit 137) | Heap plus non-heap above the container limit |
| INC-11 | Deadlock / blocked threads | Two locks taken in opposite order |
| INC-12 | Race condition under load | Lost update on a balance |
| INC-13 | Cache growth or stale data | No TTL or eviction |

**B. Messaging and data**

| ID | Incident | Typical trigger |
|---|---|---|
| INC-14 | Kafka consumer lag grows | Slow consumer at month-end volume |
| INC-15 | Poison message, repeated retries | Bad payload from upstream |
| INC-16 | Duplicate transactions | Client retry plus non-idempotent processing |
| INC-17 | Database row lock / lock wait | Long transaction blocks others |

**C. Deployment, configuration, infrastructure**

| ID | Incident | Typical trigger |
|---|---|---|
| INC-18 | 5xx spike after a deployment | Breaking change or bad configuration |
| INC-19 | Service will not start | Wrong configuration value, missing secret |
| INC-20 | Disk full from logs | DEBUG logging left on |

**D. Structured walkthroughs (tabletop)**

| ID | Incident |
|---|---|
| INC-21 | TLS certificate expiry breaks a partner API |
| INC-22 | Nightly batch overruns into business hours |
| INC-23 | Month-end / salary-day traffic spike |
| INC-24 | Time zone / DST bug in date filters |
| INC-25 | Bad data from an upstream system |
| INC-26 | Secret rotation breaks database connectivity |
| INC-27 | DNS or network change makes a service unreachable |

Faults for an incident arrive as a separate incident pack, applied without
reading the diff, just as production changes arrive without the on-call
engineer knowing what is wrong.

### Running an incident pack

1. Branch from `main` (`git checkout -b incident/inc-xx`) and merge the pack
   there, without reading its diff. `main` stays clean until the fix exists.
2. Follow the pack's setup steps (rebuild, traffic, data), then wait for the alert.
3. Investigate, fix and verify on the incident branch; write the report in
   `docs/production-debugging/incidents/`.
4. Merge the branch into `main` (release + fix + report) and push.

## Tracker

An incident is complete only after the investigation steps were performed and
recovery was verified, not after reading an explanation.

| ID | Tools used | Reproduced | Evidence | Root cause | Fix verified | Report | Interview story |
|---|---|---|---|---|---|---|---|
| INC-01 | | | | | | | |

Reports go in `docs/production-debugging/incidents/INC-xx-<slug>.md`.
