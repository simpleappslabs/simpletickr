-- A Transfer is a distinct domain event: moving a quantity of an asset between two accounts
-- within the SAME portfolio. It carries no price/cost-basis field — cost basis is derived by
-- replaying the transaction+transfer stream, never stored. A transfer moves custody, not
-- portfolio inventory: only its fee (if any) affects what the portfolio holds.
CREATE TABLE transfers (
    id                      BIGSERIAL     PRIMARY KEY,
    portfolio_id            BIGINT        NOT NULL,
    listing_id              BIGINT        NOT NULL,
    quantity                NUMERIC(19, 8) NOT NULL,
    asset_fee_quantity      NUMERIC(19, 8),
    date                    DATE          NOT NULL,
    source_account_id       BIGINT        NOT NULL,
    destination_account_id  BIGINT        NOT NULL,
    notes                   TEXT,
    CONSTRAINT fk_transfers_portfolio            FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_listing              FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_source_account       FOREIGN KEY (source_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_destination_account  FOREIGN KEY (destination_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT transfers_accounts_differ   CHECK (source_account_id <> destination_account_id),
    CONSTRAINT transfers_quantity_positive CHECK (quantity > 0),
    CONSTRAINT transfers_fee_valid CHECK (
        asset_fee_quantity IS NULL OR (asset_fee_quantity >= 0 AND asset_fee_quantity < quantity)
    )
);

CREATE INDEX transfers_portfolio_id_idx ON transfers(portfolio_id);
CREATE INDEX transfers_listing_id_idx ON transfers(listing_id);
