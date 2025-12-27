CREATE TABLE legacy_tag (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL
);

CREATE INDEX idx_legacy_tag_user_id ON legacy_tag(user_id);
CREATE UNIQUE INDEX idx_legacy_tag_user_name ON legacy_tag(user_id, name);
