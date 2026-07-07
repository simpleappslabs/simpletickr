CREATE TABLE crypto_trades (
    id           BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE
);

ALTER TABLE transactions
    ADD COLUMN trade_id BIGINT REFERENCES crypto_trades(id) ON DELETE CASCADE;

CREATE INDEX transactions_trade_id_idx ON transactions(trade_id)
    WHERE trade_id IS NOT NULL;
