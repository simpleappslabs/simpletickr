-- Set exchange names on primary listings (created by V8 with no exchange)
UPDATE listings SET exchange = 'NASDAQ'
WHERE ticker IN ('AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'QQQ')
  AND exchange IS NULL;

UPDATE listings SET exchange = 'NYSE Arca'
WHERE ticker IN ('SPY', 'VTI')
  AND exchange IS NULL;

-- VWCE's original listing (no exchange) is Euronext Amsterdam
UPDATE listings SET exchange = 'Euronext Amsterdam'
WHERE ticker = 'VWCE' AND exchange IS NULL;

-- Add Euronext Amsterdam and XETRA listings for US stocks
INSERT INTO listings (asset_id, exchange, ticker, currency)
SELECT a.id, e.exchange, l.ticker, 'EUR'
FROM listings l
JOIN assets a ON a.id = l.asset_id
CROSS JOIN (VALUES ('Euronext Amsterdam'), ('XETRA')) AS e(exchange)
WHERE l.ticker IN ('AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META')
  AND l.exchange = 'NASDAQ'
  AND NOT EXISTS (
      SELECT 1 FROM listings
      WHERE asset_id = a.id AND ticker = l.ticker AND exchange = e.exchange
  );

-- Map new European stock listings to Yahoo Finance symbols (ticker.AS / ticker.DE)
INSERT INTO price_provider_mappings (listing_id, provider, external_id)
SELECT l.id, 'YAHOO',
    CASE l.exchange
        WHEN 'Euronext Amsterdam' THEN l.ticker || '.AS'
        WHEN 'XETRA'             THEN l.ticker || '.DE'
    END
FROM listings l
WHERE l.ticker IN ('AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META')
  AND l.exchange IN ('Euronext Amsterdam', 'XETRA')
ON CONFLICT (listing_id, provider) DO NOTHING;
