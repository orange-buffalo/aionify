CREATE TABLE goals_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    daily_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_goal_minutes INTEGER NOT NULL DEFAULT 0
);

INSERT INTO goals_settings (user_id)
SELECT id FROM app_user;

CREATE INDEX idx_goals_settings_user_id ON goals_settings(user_id);

CREATE TABLE daily_goal_break (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goals_settings_id BIGINT NOT NULL REFERENCES goals_settings(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    from_time TIME NOT NULL,
    to_time TIME NOT NULL
);

CREATE INDEX idx_daily_goal_break_settings_id ON daily_goal_break(goals_settings_id);
