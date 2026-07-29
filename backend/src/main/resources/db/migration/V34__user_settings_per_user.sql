-- user_settings was a single hardcoded row (id=1) shared by the whole instance.
-- Make it one row per user instead, keyed by user_id.
ALTER TABLE user_settings DROP CONSTRAINT single_row;

ALTER TABLE user_settings ADD COLUMN user_id BIGINT REFERENCES users(id);
UPDATE user_settings SET user_id = 1;
ALTER TABLE user_settings ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE user_settings DROP CONSTRAINT pk_user_settings;
ALTER TABLE user_settings DROP COLUMN id;
ALTER TABLE user_settings ADD CONSTRAINT pk_user_settings PRIMARY KEY (user_id);
