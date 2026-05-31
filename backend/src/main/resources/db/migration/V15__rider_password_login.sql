-- Add password_hash column for rider phone+password login
SET @password_hash_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'rider_profiles'
      AND COLUMN_NAME = 'password_hash'
);
SET @add_password_hash_sql = IF(
    @password_hash_column_exists = 0,
    'ALTER TABLE rider_profiles ADD COLUMN password_hash VARCHAR(255) DEFAULT NULL',
    'SELECT 1'
);
PREPARE add_password_hash_stmt FROM @add_password_hash_sql;
EXECUTE add_password_hash_stmt;
DEALLOCATE PREPARE add_password_hash_stmt;

-- Set default password (888888) for existing riders using SHA-256
-- This is a placeholder; actual hashing will be done in application code
UPDATE rider_profiles
SET password_hash = CONCAT('{sha256}', SHA2(CONCAT('888888', ':', phone), 256))
WHERE password_hash IS NULL;

-- Allow multiple riders per area (remove UNIQUE constraint)
ALTER TABLE dispatch_area_bindings
    DROP INDEX uk_dispatch_area_bindings_area_code,
    ADD INDEX idx_dispatch_area_bindings_area_code (area_code);
