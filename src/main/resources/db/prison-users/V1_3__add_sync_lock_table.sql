-- Lock table for serializing concurrent sync operations per legacyStaffId.
-- Each row represents an active sync; holding the row lock serializes competing requests.
CREATE TABLE sync_lock (
  legacy_staff_id BIGINT PRIMARY KEY,
  locked_at TIMESTAMP NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);
