CREATE TABLE customers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL UNIQUE,
    source VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE customer_addresses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE package_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_code VARCHAR(32) NOT NULL UNIQUE,
    package_name VARCHAR(64) NOT NULL,
    total_meals INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE meal_wallets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    package_plan_id BIGINT NOT NULL,
    total_meals INT NOT NULL,
    reserved_meals INT NOT NULL DEFAULT 0,
    consumed_meals INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE wallet_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    meal_delta INT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE daily_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    serve_date DATE NOT NULL,
    source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE meal_slot_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    daily_order_id BIGINT NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    quantity INT NOT NULL,
    address_id BIGINT NOT NULL,
    note VARCHAR(255),
    status VARCHAR(32) NOT NULL
);
CREATE TABLE subscription_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    lunch_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    lunch_quantity INT NOT NULL DEFAULT 0,
    dinner_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dinner_quantity INT NOT NULL DEFAULT 0
);
CREATE TABLE dispatch_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL,
    rider_name VARCHAR(64) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL
);
CREATE TABLE delivery_receipts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL,
    receipt_url VARCHAR(255) NOT NULL,
    receipt_note VARCHAR(255),
    delivered_at TIMESTAMP NOT NULL
);
CREATE TABLE notification_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
