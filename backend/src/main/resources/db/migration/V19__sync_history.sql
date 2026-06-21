CREATE TABLE sync_history
(
    id          BIGSERIAL   NOT NULL,
    type        VARCHAR(10) NOT NULL,
    trigger     VARCHAR(10) NOT NULL,
    status      VARCHAR(10) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_ms BIGINT      NOT NULL,
    synced      INT         NOT NULL,
    failed      INT         NOT NULL,
    CONSTRAINT pk_sync_history PRIMARY KEY (id),
    CONSTRAINT chk_sync_type    CHECK (type    IN ('PRICE', 'FX')),
    CONSTRAINT chk_sync_trigger CHECK (trigger IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT chk_sync_status  CHECK (status  IN ('SUCCESS', 'FAILED', 'PARTIAL'))
);

CREATE INDEX idx_sync_history_type_started ON sync_history (type, started_at DESC);
