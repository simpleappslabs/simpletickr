-- Remove seeded assets that have never been traded; cascade deletes their listings,
-- price mappings, and price history. Assets with transactions are left untouched.
DELETE FROM assets
WHERE ticker IN ('AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META',
                 'SPY', 'QQQ', 'VTI', 'VWCE', 'BTC', 'ETH')
  AND NOT EXISTS (
      SELECT 1 FROM transactions t
      JOIN listings l ON l.id = t.listing_id
      WHERE l.asset_id = assets.id
  );
