-- Populate user_settings for all existing users with Monday as default
INSERT INTO user_settings (user_id, start_of_week)
SELECT id, 'MONDAY'
FROM app_user;
