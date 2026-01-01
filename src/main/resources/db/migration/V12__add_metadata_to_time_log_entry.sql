-- Add metadata column to time_log_entry table
ALTER TABLE time_log_entry ADD COLUMN metadata TEXT[] NOT NULL DEFAULT '{}';
