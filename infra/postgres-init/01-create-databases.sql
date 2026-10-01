CREATE DATABASE auth_db;
CREATE DATABASE user_db;
CREATE DATABASE transaction_db;
CREATE DATABASE budget_db;
-- Milestone 15 — analytics-service's own R2DBC-backed database, gaining real
-- persistence (dashboard_query_log) for the first time at this milestone;
-- schema-per-service, same isolation every other *_db entry above already has.
CREATE DATABASE analytics_db;
