-- Milestone 13 — multi-currency support (Functional Roadmap: Transaction
-- Service responsibility #5, Workflow 1 Step 2), built now because this is
-- the first point the Technical Roadmap requires a real external
-- exchange-rate-API call to exist, to isolate with a ThreadPoolBulkhead.
--
-- NOT NULL DEFAULT lets any pre-existing row backfill to 'INR' (the
-- platform's own worked examples are in INR) before the DEFAULT is dropped,
-- so every row inserted from this migration onward must supply a currency
-- explicitly, matching CreateTransactionRequest.currency() now being
-- required. base_currency_amount stays nullable — it is only populated when
-- a transaction's currency differs from the user's preferred/base currency
-- and an actual conversion happened; a same-currency transaction has nothing
-- to convert.
ALTER TABLE transactions
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN base_currency_amount NUMERIC(19, 2);

ALTER TABLE transactions ALTER COLUMN currency DROP DEFAULT;
