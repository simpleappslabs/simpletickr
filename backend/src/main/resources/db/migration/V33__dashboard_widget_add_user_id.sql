ALTER TABLE dashboard_widget ADD COLUMN user_id BIGINT REFERENCES users(id);

UPDATE dashboard_widget SET user_id = 1;

ALTER TABLE dashboard_widget ALTER COLUMN user_id SET NOT NULL;
