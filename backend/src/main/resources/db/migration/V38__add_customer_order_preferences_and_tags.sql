CREATE TABLE IF NOT EXISTS customer_order_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    default_user_remark VARCHAR(255) NULL,
    default_merchant_remark VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_order_preferences_customer (customer_id)
);

CREATE TABLE IF NOT EXISTS customer_order_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    tag_code VARCHAR(64) NOT NULL,
    tag_name VARCHAR(64) NOT NULL,
    tag_source VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NULL,
    remaining_uses INT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_address_change_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL,
    from_address_id BIGINT NOT NULL,
    to_address_id BIGINT NOT NULL,
    change_mode VARCHAR(32) NOT NULL,
    operator_name VARCHAR(64) NULL,
    operator_role VARCHAR(32) NULL,
    reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
