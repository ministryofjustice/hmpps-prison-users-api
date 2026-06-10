-- Drop index on users table
DROP INDEX IF EXISTS ux_user_email;

-- Remove email from users table
ALTER TABLE users
DROP COLUMN IF EXISTS email;
