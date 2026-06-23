ALTER TABLE transactions ADD COLUMN external_id TEXT;

CREATE UNIQUE INDEX transactions_portfolio_external_id_uidx
    ON transactions (portfolio_id, external_id)
    WHERE external_id IS NOT NULL;
