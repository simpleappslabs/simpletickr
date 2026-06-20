-- Snapshot of the FX rate at transaction execution time.
-- NULL means the listing currency equals the portfolio base currency (no conversion needed).
-- For cross-currency transactions recorded before this migration, NULL indicates MISSING historical data.
ALTER TABLE transactions ADD COLUMN fx_rate NUMERIC(19, 8);
