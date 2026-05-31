ALTER TABLE customers ADD COLUMN customer_status VARCHAR(32) NOT NULL DEFAULT 'INTENTION';
ALTER TABLE customers ADD COLUMN registered_at TIMESTAMP NULL;
ALTER TABLE customers ADD COLUMN first_paid_at TIMESTAMP NULL;
ALTER TABLE customers ADD COLUMN last_order_at TIMESTAMP NULL;
ALTER TABLE customers ADD COLUMN last_login_at TIMESTAMP NULL;
ALTER TABLE customers ADD COLUMN source_channel VARCHAR(32) NULL;
ALTER TABLE customers ADD COLUMN remark VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN current_openid VARCHAR(64) NULL;
ALTER TABLE customers ADD COLUMN openid_updated_at TIMESTAMP NULL;
ALTER TABLE customers ADD COLUMN is_priority_customer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN priority_tag VARCHAR(64) NULL;
ALTER TABLE customers ADD COLUMN priority_note VARCHAR(255) NULL;

UPDATE customers
SET customer_status = CASE
    WHEN EXISTS (
        SELECT 1
        FROM meal_wallets mw
        WHERE mw.customer_id = customers.id
          AND mw.active = TRUE
          AND (mw.total_meals - mw.reserved_meals - mw.consumed_meals) > 0
    ) THEN 'FORMAL'
    ELSE 'INTENTION'
END,
registered_at = COALESCE(created_at, CURRENT_TIMESTAMP),
source_channel = COALESCE(source, 'UNKNOWN'),
current_openid = openid;

ALTER TABLE meal_wallets ADD COLUMN opened_at TIMESTAMP NULL;
ALTER TABLE meal_wallets ADD COLUMN expired_at TIMESTAMP NULL;
ALTER TABLE meal_wallets ADD COLUMN last_adjusted_at TIMESTAMP NULL;

UPDATE meal_wallets
SET opened_at = CURRENT_TIMESTAMP,
    last_adjusted_at = CURRENT_TIMESTAMP
WHERE opened_at IS NULL;

ALTER TABLE wallet_transactions ADD COLUMN biz_type VARCHAR(32) NULL;
ALTER TABLE wallet_transactions ADD COLUMN related_order_id BIGINT NULL;
ALTER TABLE wallet_transactions ADD COLUMN related_aftersale_id BIGINT NULL;
ALTER TABLE wallet_transactions ADD COLUMN operator_id BIGINT NULL;
ALTER TABLE wallet_transactions ADD COLUMN snapshot_balance INT NULL;

UPDATE wallet_transactions
SET biz_type = transaction_type,
    snapshot_balance = 0
WHERE biz_type IS NULL;

ALTER TABLE subscription_rules ADD COLUMN start_date DATE NULL;
ALTER TABLE subscription_rules ADD COLUMN end_date DATE NULL;
ALTER TABLE subscription_rules ADD COLUMN default_address_id BIGINT NULL;
ALTER TABLE subscription_rules ADD COLUMN default_note VARCHAR(255) NULL;
ALTER TABLE subscription_rules ADD COLUMN is_priority_follow BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscription_rules ADD COLUMN paused BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE subscription_rules
SET start_date = CURRENT_DATE,
    end_date = CURRENT_DATE + INTERVAL '30' DAY,
    default_note = '-'
WHERE start_date IS NULL;

ALTER TABLE meal_slot_orders ADD COLUMN user_note VARCHAR(255) NULL;
ALTER TABLE meal_slot_orders ADD COLUMN admin_note VARCHAR(255) NULL;
ALTER TABLE meal_slot_orders ADD COLUMN special_tag VARCHAR(64) NULL;
ALTER TABLE meal_slot_orders ADD COLUMN is_priority BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE meal_slot_orders ADD COLUMN source_type VARCHAR(32) NULL;
ALTER TABLE meal_slot_orders ADD COLUMN confirmed_from_subscription BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE meal_slot_orders
SET user_note = note,
    source_type = 'LEGACY'
WHERE user_note IS NULL;

CREATE TABLE subscription_confirmations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serve_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    address_id BIGINT NULL,
    user_note VARCHAR(255) NULL,
    admin_note VARCHAR(255) NULL,
    is_priority BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by VARCHAR(64) NULL,
    confirmed_at TIMESTAMP NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE aftersale_cases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    issue_type VARCHAR(32) NOT NULL,
    issue_desc VARCHAR(255) NULL,
    resolution_type VARCHAR(32) NOT NULL,
    rollback_meal BOOLEAN NOT NULL DEFAULT FALSE,
    bonus_meals INT NOT NULL DEFAULT 0,
    compensation_item VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE aftersale_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aftersale_case_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    action_note VARCHAR(255) NULL,
    operator_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cost_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cost_date DATE NOT NULL,
    cost_category VARCHAR(32) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    remark VARCHAR(255) NULL,
    recorded_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operating_metrics_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    total_sales DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_profit DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_orders INT NOT NULL DEFAULT 0,
    total_meals INT NOT NULL DEFAULT 0,
    special_orders INT NOT NULL DEFAULT 0,
    aftersale_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_operating_metrics_daily_stat_date UNIQUE (stat_date)
);
