CREATE TABLE asset_price_history
(
    listing_id  BIGINT         NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    date        DATE           NOT NULL,
    close_price NUMERIC(19, 6) NOT NULL,
    PRIMARY KEY (listing_id, date)
);

CREATE INDEX idx_price_history_listing_date ON asset_price_history (listing_id, date DESC);
