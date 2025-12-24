-- Remove language_code column as we'll derive language from locale
ALTER TABLE app_user DROP COLUMN language_code;
