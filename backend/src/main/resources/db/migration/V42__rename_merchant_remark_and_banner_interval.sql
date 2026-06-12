ALTER TABLE customers
    CHANGE COLUMN remark merchant_remark VARCHAR(255) NULL;

ALTER TABLE meal_slot_orders
    CHANGE COLUMN admin_note merchant_remark VARCHAR(255) NULL;

ALTER TABLE subscription_rules
    CHANGE COLUMN default_note merchant_remark VARCHAR(255) NULL;

ALTER TABLE subscription_confirmations
    CHANGE COLUMN admin_note merchant_remark VARCHAR(255) NULL;

ALTER TABLE admin_settings
    ADD COLUMN banner_interval_seconds INT NOT NULL DEFAULT 3;
