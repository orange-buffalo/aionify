-- Allow multiple named API access tokens per user, so that each integration can use its own revocable token

ALTER TABLE user_api_access_token DROP CONSTRAINT user_api_access_token_user_id_key;

ALTER TABLE user_api_access_token ADD COLUMN name VARCHAR(100);
ALTER TABLE user_api_access_token ADD COLUMN created_at TIMESTAMP;

UPDATE user_api_access_token SET name = 'Default', created_at = CURRENT_TIMESTAMP;

ALTER TABLE user_api_access_token ALTER COLUMN name SET NOT NULL;
ALTER TABLE user_api_access_token ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE user_api_access_token ADD CONSTRAINT uq_user_api_access_token_user_name UNIQUE (user_id, name);
ALTER TABLE user_api_access_token ADD CONSTRAINT uq_user_api_access_token_token UNIQUE (token);
