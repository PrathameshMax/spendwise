# SpendWise (monorepo, archived)

This repository is read-only. In November 2026 SpendWise was split into one repository per
service, each with its own CI pipeline and container image. All new work happens there.

| Start here | |
|---|---|
| Run the whole system | [spendwise-platform](https://github.com/PrathameshMax/spendwise-platform) |
| Shared parent POM, libraries, Avro and gRPC contracts | [spendwise-shared](https://github.com/PrathameshMax/spendwise-shared) |
| Configuration | [spendwise-config](https://github.com/PrathameshMax/spendwise-config) |

Services: [discovery-server](https://github.com/PrathameshMax/spendwise-discovery-server) ·
[config-server](https://github.com/PrathameshMax/spendwise-config-server) ·
[api-gateway](https://github.com/PrathameshMax/spendwise-api-gateway) ·
[user-service](https://github.com/PrathameshMax/spendwise-user-service) ·
[transaction-service](https://github.com/PrathameshMax/spendwise-transaction-service) ·
[budget-service](https://github.com/PrathameshMax/spendwise-budget-service) ·
[notification-service](https://github.com/PrathameshMax/spendwise-notification-service) ·
[analytics-service](https://github.com/PrathameshMax/spendwise-analytics-service) ·
[web](https://github.com/PrathameshMax/spendwise-web)

Each new repository was extracted with `git filter-repo`, so its files keep their full history from
this one. `auth-service` was retired rather than moved: Keycloak replaced it. The copies of
`spendwise-common` and `spendwise-events` left here are stale; use the released versions from
spendwise-shared. The milestone-by-milestone notes are in spendwise-platform under
`docs/monorepo-milestones.md`.
