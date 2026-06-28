WITH aapl AS (
    INSERT INTO assets (name, type) VALUES ('Apple Inc.', 'STOCK') RETURNING id
)
INSERT INTO listings (asset_id, ticker, currency, exchange)
SELECT id, 'AAPL', 'USD', 'NASDAQ' FROM aapl;

WITH btc AS (
    INSERT INTO assets (name, type) VALUES ('Bitcoin', 'CRYPTO') RETURNING id
)
INSERT INTO listings (asset_id, ticker, currency)
SELECT id, 'BTC', 'USD' FROM btc;

WITH vwce AS (
    INSERT INTO assets (name, type) VALUES ('Vanguard FTSE All-World', 'ETF') RETURNING id
)
INSERT INTO listings (asset_id, ticker, currency, exchange)
SELECT id, 'VWCE', 'EUR', 'Euronext Amsterdam' FROM vwce;
