CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,

    type VARCHAR(20) NOT NULL,

    category VARCHAR(100) NOT NULL,

    description VARCHAR(500),

    transaction_date DATE NOT NULL,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_transactions_user_id
ON transactions(user_id);