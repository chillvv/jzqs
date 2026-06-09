CREATE TABLE customer_delivery_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    meal_slot_order_id BIGINT NOT NULL,
    template_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(64) NOT NULL,
    authorized_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    last_error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_delivery_subscriptions_order UNIQUE (meal_slot_order_id)
);

CREATE INDEX idx_customer_delivery_subscriptions_customer_status
    ON customer_delivery_subscriptions (customer_id, status);
