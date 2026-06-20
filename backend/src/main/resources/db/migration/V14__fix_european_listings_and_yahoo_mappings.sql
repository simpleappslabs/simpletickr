-- US stocks do not trade on Euronext Amsterdam; remove those listings
-- (price_provider_mappings cascade-deleted via FK)
DELETE FROM listings
WHERE ticker IN ('AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META')
  AND exchange = 'Euronext Amsterdam';

-- Alphabet trades on XETRA as ABEA.DE (not GOOGL.DE)
UPDATE price_provider_mappings
SET external_id = 'ABEA.DE'
WHERE provider = 'YAHOO'
  AND listing_id = (SELECT id FROM listings WHERE ticker = 'GOOGL' AND exchange = 'XETRA');

-- Meta kept its pre-rebrand German ticker FB2A.DE (not META.DE)
UPDATE price_provider_mappings
SET external_id = 'FB2A.DE'
WHERE provider = 'YAHOO'
  AND listing_id = (SELECT id FROM listings WHERE ticker = 'META' AND exchange = 'XETRA');
