-- Correct crypto listing currencies from USD to EUR
UPDATE listings SET currency = 'EUR' WHERE ticker IN ('BTC', 'ETH');

-- Add XETRA listing for VWCE (the existing listing covers Euronext Amsterdam)
INSERT INTO listings (asset_id, exchange, ticker, currency)
SELECT a.id, 'XETRA', 'VWCE', 'EUR'
FROM assets a
JOIN listings l ON l.asset_id = a.id AND l.ticker = 'VWCE'
WHERE NOT EXISTS (
    SELECT 1 FROM listings WHERE asset_id = a.id AND ticker = 'VWCE' AND exchange = 'XETRA'
);

-- Map all seeded listings to Yahoo Finance symbols
INSERT INTO price_provider_mappings (listing_id, provider, external_id)
SELECT l.id, 'YAHOO', m.external_id
FROM (VALUES
    -- US stocks & ETFs: Yahoo symbol = ticker
    ('AAPL',  NULL,    'AAPL'),
    ('MSFT',  NULL,    'MSFT'),
    ('GOOGL', NULL,    'GOOGL'),
    ('AMZN',  NULL,    'AMZN'),
    ('NVDA',  NULL,    'NVDA'),
    ('META',  NULL,    'META'),
    ('SPY',   NULL,    'SPY'),
    ('QQQ',   NULL,    'QQQ'),
    ('VTI',   NULL,    'VTI'),
    -- EUR ETF: both exchange listings
    ('VWCE',  NULL,    'VWCE.AS'),
    ('VWCE',  'XETRA', 'VWCE.DE'),
    -- Crypto: EUR pairs
    ('BTC',   NULL,    'BTC-EUR'),
    ('ETH',   NULL,    'ETH-EUR')
) AS m(ticker, exchange, external_id)
JOIN listings l ON l.ticker = m.ticker
    AND (m.exchange IS NULL AND l.exchange IS NULL
      OR l.exchange = m.exchange)
ON CONFLICT (listing_id, provider) DO NOTHING;
