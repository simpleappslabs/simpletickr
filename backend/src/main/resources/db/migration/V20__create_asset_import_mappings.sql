CREATE TABLE asset_import_mappings (
    id            BIGSERIAL PRIMARY KEY,
    broker        VARCHAR(64) NOT NULL,
    external_name TEXT NOT NULL,
    asset_id      BIGINT NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (broker, external_name)
);
