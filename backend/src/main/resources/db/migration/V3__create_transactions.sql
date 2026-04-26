CREATE TABLE transactions
(
    id           BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT         NOT NULL REFERENCES portfolios (id),
    asset_id     BIGINT         NOT NULL REFERENCES assets (id),
    type         VARCHAR(10)    NOT NULL,
    quantity     NUMERIC(19, 8) NOT NULL,
    price        NUMERIC(19, 6) NOT NULL,
    date         DATE           NOT NULL,
    fees         NUMERIC(19, 6)
);