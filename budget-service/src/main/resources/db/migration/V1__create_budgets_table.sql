CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category VARCHAR(100) NOT NULL,
    capped_amount NUMERIC(19, 2) NOT NULL,
    current_spend NUMERIC(19, 2) NOT NULL DEFAULT 0,
    period_month VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_budgets_user_category_period UNIQUE (user_id, category, period_month)
);

CREATE INDEX idx_budgets_user_id_period_month ON budgets (user_id, period_month);
