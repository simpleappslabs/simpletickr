CREATE TABLE accounts (
    id             BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    broker         VARCHAR(255),
    account_type   VARCHAR(20)  NOT NULL,
    currency       VARCHAR(3),
    account_number VARCHAR(255),
    institution    VARCHAR(255)
);

-- Migrate existing broker strings: one account per distinct non-null value
INSERT INTO accounts (name, broker, account_type)
SELECT DISTINCT broker, broker, 'BROKERAGE'
FROM transactions
WHERE broker IS NOT NULL;

-- Catch-all for transactions with no broker
INSERT INTO accounts (name, account_type)
VALUES ('Default', 'BROKERAGE');

ALTER TABLE transactions
    ADD COLUMN account_id BIGINT CONSTRAINT fk_transactions_account REFERENCES accounts (id) ON DELETE RESTRICT;

-- Link broker transactions to their migrated account
UPDATE transactions t
SET account_id = a.id
FROM accounts a
WHERE t.broker = a.name;

-- Link null-broker transactions to Default
UPDATE transactions t
SET account_id = a.id
FROM accounts a
WHERE t.broker IS NULL AND a.name = 'Default';

ALTER TABLE transactions ALTER COLUMN account_id SET NOT NULL;

ALTER TABLE transactions DROP COLUMN broker;
