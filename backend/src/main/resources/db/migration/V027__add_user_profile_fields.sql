-- Add first_name and last_name to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);

-- Backfill null email rows with a placeholder derived from username to avoid unique constraint issues
-- when email becomes required going forward (existing rows keep null unless they have a username to derive from)
-- We do NOT force NOT NULL here to preserve backward compatibility for existing DB users.
-- New signups will require email via application validation.
