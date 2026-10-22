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
| Kibana, Splunk, AppDynamics, Grafana, Zipkin | Restart or scale a service |
| Postman: business APIs and secured Actuator endpoints | Thread dump or heap dump from inside a container |
| Reading code and configuration in the repository | Database queries, locks, explain plans |
| | Deploying a fix (change request + approval) |

Locally, `docker` and `psql` play the role of the platform team's and DBA's
tools. They are not used during an investigation unless the request was made.

## Lab components

| Component | Where | Notes |
|---|---|---|
| Production mode | `docker-compose.lab.yml` | `prod` profile (root WARN, com.spendwise INFO), ECS JSON logs, compose labels on every container log |
| Test data | `POST/GET/DELETE /lab/seed` on transaction-service (8083) | `lab` profile only; realistic Indian retail-banking ledger; lab ids start with `1ab5eed0` and are removed without touching real data |
| Central logging (Kibana) | `infra/elk/` | Fluent Bit -> Elasticsearch -> Kibana, data view "SpendWise logs" |
| Central logging (Splunk) | planned | Same Fluent Bit pipeline, Splunk HEC output |
| APM (AppDynamics) | planned | SaaS trial controller + Java agent on 2-3 services |
| Requests | `postman/SpendWise.postman_collection.json` | Folder "Production Debugging Lab" |
| Start / stop | `scripts/lab.sh` | `start`, `stop`, `status`, `logs-start`, `logs-stop`, `logs-status` |

Run one log platform at a time on a 16 GB laptop: the app stack uses about
7 GB, Elasticsearch + Kibana about 2.5 GB.

### Searching logs in Kibana

Field names are flattened with underscores. Useful queries in Discover:

| Question | Query |
|---|---|
| One request across every service | `correlationId : "lab-1729600000000"` |
| One trace (same as Zipkin) | `traceId : "<id>"` |
| Errors from one service | `service_name : "transaction-service" and log_level : "ERROR"` |
| Exceptions of a type | `error_type : *SQLTransientConnectionException*` |
| Infrastructure containers (plain text) | `container_name : "postgres"` |

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

## Tracker

An incident is complete only after the investigation steps were performed and
recovery was verified, not after reading an explanation.

| ID | Tools used | Reproduced | Evidence | Root cause | Fix verified | Report | Interview story |
|---|---|---|---|---|---|---|---|
| INC-01 | | | | | | | |

Reports go in `docs/production-debugging/incidents/INC-xx-<slug>.md`.
