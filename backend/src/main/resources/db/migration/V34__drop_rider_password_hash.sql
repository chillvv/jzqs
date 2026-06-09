SET @rider_password_hash_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'rider_profiles'
      AND COLUMN_NAME = 'password_hash'
);

SET @drop_rider_password_hash_sql = IF(
    @rider_password_hash_exists = 1,
    'ALTER TABLE rider_profiles DROP COLUMN password_hash',
    'SELECT 1'
);

PREPARE drop_rider_password_hash_stmt FROM @drop_rider_password_hash_sql;
EXECUTE drop_rider_password_hash_stmt;
DEALLOCATE PREPARE drop_rider_password_hash_stmt;
