ALTER TABLE admin_settings
    ADD COLUMN package_expiry_reminder_days INT NOT NULL DEFAULT 7,
    ADD COLUMN package_low_balance_threshold INT NOT NULL DEFAULT 3;

UPDATE admin_settings
SET package_expiry_reminder_days = 7,
    package_low_balance_threshold = 3
WHERE id = 1;
