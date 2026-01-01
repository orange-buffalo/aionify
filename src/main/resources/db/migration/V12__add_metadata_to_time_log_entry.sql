-- Add metadata column to time_log_entry table for storing arbitrary JSON data
ALTER TABLE time_log_entry ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::JSONB;
