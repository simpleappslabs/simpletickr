ALTER TABLE assets ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE portfolios ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX assets_uuid_uidx ON assets (uuid);
CREATE UNIQUE INDEX portfolios_uuid_uidx ON portfolios (uuid);
