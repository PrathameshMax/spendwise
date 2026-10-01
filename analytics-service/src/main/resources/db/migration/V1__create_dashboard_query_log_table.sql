-- Milestone 15 — id defaults to gen_random_uuid() (built into Postgres core
-- since version 13, no pgcrypto extension needed on this platform's
-- postgres:16-alpine image), not application-assigned the way budget-service's
-- own Hibernate-backed `budgets.id` is: Spring Data R2DBC's default new-vs-
-- existing detection for a reference-typed @Id is "null means new", so
-- DashboardQueryLog.newEntry() leaves id null and lets this default generate
-- it, with r2dbc-postgresql returning the generated value on the same INSERT.
CREATE TABLE dashboard_query_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    item_count INTEGER NOT NULL,
    queried_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_dashboard_query_log_user_id_period_month ON dashboard_query_log (user_id, period_month);
