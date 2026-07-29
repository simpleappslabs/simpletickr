ALTER TABLE accounts ADD COLUMN user_id BIGINT REFERENCES users(id);

UPDATE accounts SET user_id = 1;

ALTER TABLE accounts ALTER COLUMN user_id SET NOT NULL;
