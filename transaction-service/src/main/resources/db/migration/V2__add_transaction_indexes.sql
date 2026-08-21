CREATE INDEX IF NOT EXISTS idx_transactions_user_id
ON transactions(user_id);

CREATE INDEX IF NOT EXISTS idx_transactions_transaction_date
ON transactions(transaction_date);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id_transaction_date
ON transactions(user_id, transaction_date);