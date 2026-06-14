ALTER TABLE assets ADD COLUMN isin VARCHAR(12);

CREATE TABLE listings
(
    id       BIGSERIAL PRIMARY KEY,
    asset_id BIGINT      NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    exchange VARCHAR(100),
    ticker   VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL
);

INSERT INTO listings (asset_id, ticker, currency)
SELECT id, ticker, currency
FROM assets;

ALTER TABLE assets DROP COLUMN ticker;
ALTER TABLE assets DROP COLUMN currency;
ALTER TABLE assets DROP COLUMN current_price;
