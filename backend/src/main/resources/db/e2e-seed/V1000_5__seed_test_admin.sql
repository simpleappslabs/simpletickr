-- Fixed-password test admin for Playwright: username 'e2e-admin', password 'TestPassword123!'.
-- Seeded before the app's BootstrapAdminRunner check runs, so it sees identities already exist
-- and skips generating a random bootstrap admin — Playwright gets a stable, known credential.
WITH new_user AS (
    INSERT INTO users (username) VALUES ('e2e-admin') RETURNING id
), new_org AS (
    INSERT INTO organizations (name) VALUES ('e2e-admin''s organization') RETURNING id
)
INSERT INTO memberships (user_id, organization_id, role)
SELECT new_user.id, new_org.id, 'OWNER' FROM new_user, new_org;

INSERT INTO identities (user_id, provider_type, provider_id, subject, password_hash)
SELECT id, 'LOCAL', 'local', NULL, '$argon2id$v=19$m=16384,t=2,p=1$TgJcP7fYvua2e3e6gV2LeQ$IAIbjufynaHyjPbFjS0WNrIYVXt6cB+Cg5YbFp0prVE'
FROM users WHERE username = 'e2e-admin';
