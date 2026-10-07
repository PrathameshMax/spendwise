-- One database per service (schema-per-service isolation since Milestone 4).
--
-- Milestone 17 fix (raised validating Milestone 15: analytics_db was missing
-- on an existing Postgres volume) — rewritten to be idempotent and run on
-- every `docker compose up`, not only on a fresh volume. Postgres has no
-- CREATE DATABASE IF NOT EXISTS, and CREATE DATABASE cannot run inside a
-- function or DO block, so each line uses psql's \gexec: the SELECT emits the
-- CREATE DATABASE statement as text only when the database is absent, and
-- \gexec executes whatever the SELECT returned (nothing, if it already exists).
--
-- The same file is consumed two ways, both through psql:
--   1. postgres's own /docker-entrypoint-initdb.d — first start of a fresh volume.
--   2. the postgres-db-init one-shot container in docker-compose.yml — every
--      start, which is what covers volumes created before a database was added.
-- Adding a service database means adding one line here; nothing else.
SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE user_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'user_db')\gexec
SELECT 'CREATE DATABASE transaction_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'transaction_db')\gexec
SELECT 'CREATE DATABASE budget_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'budget_db')\gexec
SELECT 'CREATE DATABASE analytics_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_db')\gexec
-- Milestone 19 — notification-service's idempotency store and alert log.
SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec
