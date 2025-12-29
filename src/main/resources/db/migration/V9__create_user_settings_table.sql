CREATE TABLE user_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    start_of_week VARCHAR(20) NOT NULL DEFAULT 'MONDAY'
);

CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);
