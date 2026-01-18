CREATE TABLE remember_me_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    user_agent VARCHAR(500),
    CONSTRAINT fk_remember_me_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_remember_me_token_hash ON remember_me_token(token_hash);
CREATE INDEX idx_remember_me_token_expires_at ON remember_me_token(expires_at);
CREATE INDEX idx_remember_me_token_user_id ON remember_me_token(user_id);
