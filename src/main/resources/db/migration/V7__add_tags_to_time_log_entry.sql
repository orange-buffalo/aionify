-- Add tags column to time_log_entry table
ALTER TABLE time_log_entry ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';
