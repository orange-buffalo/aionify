CREATE TABLE user_api_access_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    token VARCHAR(100) NOT NULL,
    CONSTRAINT fk_user_api_access_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
