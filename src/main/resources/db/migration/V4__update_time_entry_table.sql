-- Truncate the time_entry table as required
TRUNCATE TABLE time_entry CASCADE;

-- Add new columns to time_entry table
ALTER TABLE time_entry
    ADD COLUMN end_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN title VARCHAR(1000) NOT NULL,
    ADD COLUMN owner_id BIGINT NOT NULL;

-- Add foreign key constraint to app_user
ALTER TABLE time_entry
    ADD CONSTRAINT fk_time_entry_owner 
    FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE CASCADE;

-- Add unique constraint: only one entry per owner_id with NULL end_time
-- This ensures only one active entry per user
CREATE UNIQUE INDEX idx_time_entry_active_per_user 
    ON time_entry (owner_id) 
    WHERE end_time IS NULL;

-- Add index for efficient querying by owner and time range
CREATE INDEX idx_time_entry_owner_start_time 
    ON time_entry (owner_id, start_time DESC);
