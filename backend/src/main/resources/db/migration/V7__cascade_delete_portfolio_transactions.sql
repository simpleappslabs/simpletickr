ALTER TABLE transactions
    DROP CONSTRAINT transactions_portfolio_id_fkey;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_portfolio_id_fkey
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE;
