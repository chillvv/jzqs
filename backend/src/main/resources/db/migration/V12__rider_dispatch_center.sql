CREATE TABLE rider_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rider_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    wechat_open_id VARCHAR(128),
    employment_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    default_area_code VARCHAR(64),
    display_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_rider_profiles_name ON rider_profiles (rider_name);

CREATE TABLE rider_address_bindings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    address_fingerprint VARCHAR(255) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    rider_profile_id BIGINT NOT NULL,
    manually_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_reason VARCHAR(128),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_rider_address_bindings_customer_address ON rider_address_bindings (customer_id, address_id);
CREATE INDEX idx_rider_address_bindings_fingerprint ON rider_address_bindings (address_fingerprint);

CREATE TABLE dispatch_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serve_date DATE NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    rider_profile_id BIGINT NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    batch_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    total_count INT NOT NULL DEFAULT 0,
    delivered_count INT NOT NULL DEFAULT 0,
    current_sequence INT NOT NULL DEFAULT 1,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    last_reordered_at TIMESTAMP,
    last_reordered_by VARCHAR(32)
);

CREATE INDEX idx_dispatch_batches_serve_date_meal_period ON dispatch_batches (serve_date, meal_period);
CREATE INDEX idx_dispatch_batches_rider_profile ON dispatch_batches (rider_profile_id);

CREATE TABLE dispatch_batch_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    meal_slot_order_id BIGINT NOT NULL,
    current_sequence INT NOT NULL,
    suggested_sequence INT NOT NULL,
    item_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    manually_adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    reordered_by VARCHAR(32),
    reordered_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_dispatch_batch_items_order ON dispatch_batch_items (meal_slot_order_id);
CREATE UNIQUE INDEX uk_dispatch_batch_items_batch_sequence ON dispatch_batch_items (batch_id, current_sequence);

ALTER TABLE delivery_receipts
    ADD COLUMN visible_at TIMESTAMP;

ALTER TABLE delivery_receipts
    ADD COLUMN expires_at TIMESTAMP;

ALTER TABLE delivery_receipts
    ADD COLUMN visible_to_customer BOOLEAN NOT NULL DEFAULT FALSE;
