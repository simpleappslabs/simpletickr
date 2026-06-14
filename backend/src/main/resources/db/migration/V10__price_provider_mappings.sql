CREATE TABLE price_provider_mappings
(
    id          BIGSERIAL PRIMARY KEY,
    listing_id  BIGINT       NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    provider    VARCHAR(50)  NOT NULL,
    external_id VARCHAR(50)  NOT NULL,
    UNIQUE (listing_id, provider)
);
