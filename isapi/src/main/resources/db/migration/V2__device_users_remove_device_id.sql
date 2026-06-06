-- Remove device_id column and make employee_no globally unique
-- This ensures one user exists across all devices

-- 1. Keep only the latest row per employee_no (by max id)
DELETE FROM device_users
WHERE id NOT IN (
    SELECT MAX(id)
    FROM device_users
    GROUP BY employee_no
);

-- 2. Drop old unique constraint if exists
ALTER TABLE device_users
    DROP CONSTRAINT IF EXISTS uq_device_user_employee_no;

-- 3. Drop device_id column
ALTER TABLE device_users
    DROP COLUMN IF EXISTS device_id;

-- 4. Create new unique constraint on employee_no only
ALTER TABLE device_users
    ADD CONSTRAINT uq_device_user_employee_no UNIQUE (employee_no);
