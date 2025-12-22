-- Rename time_entry table to time_log_entry
ALTER TABLE time_entry RENAME TO time_log_entry;

-- Rename the foreign key constraint
ALTER TABLE time_log_entry
    DROP CONSTRAINT fk_time_entry_owner;

ALTER TABLE time_log_entry
    ADD CONSTRAINT fk_time_log_entry_owner 
    FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE CASCADE;

-- Rename the unique index for active entries
DROP INDEX idx_time_entry_active_per_user;

CREATE UNIQUE INDEX idx_time_log_entry_active_per_user 
    ON time_log_entry (owner_id) 
    WHERE end_time IS NULL;

-- Rename the index for querying by owner and time range
DROP INDEX idx_time_entry_owner_start_time;

CREATE INDEX idx_time_log_entry_owner_start_time 
    ON time_log_entry (owner_id, start_time DESC);
