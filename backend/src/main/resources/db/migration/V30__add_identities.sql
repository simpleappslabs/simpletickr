CREATE TABLE organizations (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE memberships (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    organization_id BIGINT      NOT NULL REFERENCES organizations(id),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, organization_id)
);

CREATE TABLE identities (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    provider_type VARCHAR(20)  NOT NULL CHECK (provider_type IN ('LOCAL', 'OIDC')),
    provider_id   VARCHAR(255) NOT NULL,
    subject       VARCHAR(255),
    password_hash VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider_type),
    UNIQUE (provider_id, subject)
);

-- Backfill a personal organization + OWNER membership for the pre-existing user,
-- for consistency with every future bootstrapped user (no local identity backfilled here —
-- the real bootstrap admin identity is created at first app boot).
WITH personal_org AS (
    INSERT INTO organizations (name) VALUES ('default''s organization') RETURNING id
)
INSERT INTO memberships (user_id, organization_id, role)
SELECT 1, id, 'OWNER' FROM personal_org;
