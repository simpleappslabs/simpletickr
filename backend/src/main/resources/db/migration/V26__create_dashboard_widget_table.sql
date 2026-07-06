CREATE TABLE dashboard_widget (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(50) NOT NULL,
    config     JSONB       NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
