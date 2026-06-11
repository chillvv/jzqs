CREATE TABLE customer_notes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    note_type VARCHAR(16) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    content VARCHAR(255) NOT NULL,
    start_at TIMESTAMP NULL,
    end_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_customer_notes_customer (customer_id),
    KEY idx_customer_notes_lookup (customer_id, note_type, scope_type, is_active),
    CONSTRAINT chk_customer_notes_scope_time CHECK (
        (scope_type = 'LONG_TERM' AND start_at IS NULL AND end_at IS NULL)
        OR (scope_type = 'TIME_BOXED' AND start_at IS NOT NULL AND end_at IS NOT NULL)
    )
);
CREATE TABLE order_notes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    note_type VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    content VARCHAR(255) NOT NULL,
    effective_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_notes_order (meal_slot_order_id),
    KEY idx_order_notes_lookup (meal_slot_order_id, note_type)
);
DROP TABLE IF EXISTS customer_order_preferences;
DROP TABLE IF EXISTS customer_order_tags;
