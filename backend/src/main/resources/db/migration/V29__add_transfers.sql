-- 'TRANSFER_OUT' (12 chars) / 'TRANSFER_IN' (11 chars) exceed the current VARCHAR(10) type column.
ALTER TABLE transactions ALTER COLUMN type TYPE VARCHAR(20);

CREATE TABLE transfers (
    id BIGSERIAL PRIMARY KEY
);

ALTER TABLE transactions
    ADD COLUMN transfer_id        BIGINT REFERENCES transfers(id) ON DELETE CASCADE,
    ADD COLUMN asset_fee_quantity NUMERIC(19, 8);

CREATE INDEX transactions_transfer_id_idx ON transactions(transfer_id)
    WHERE transfer_id IS NOT NULL;
