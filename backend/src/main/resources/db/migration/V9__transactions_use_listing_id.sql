ALTER TABLE transactions ADD COLUMN listing_id BIGINT;

UPDATE transactions t
SET listing_id = (
    SELECT l.id FROM listings l WHERE l.asset_id = t.asset_id LIMIT 1
);

ALTER TABLE transactions ALTER COLUMN listing_id SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_listing FOREIGN KEY (listing_id) REFERENCES listings (id);

ALTER TABLE transactions DROP COLUMN asset_id;
