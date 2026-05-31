ALTER TABLE aftersale_cases
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'ADMIN_DIRECT',
    ADD COLUMN reason_code VARCHAR(64) NULL,
    ADD COLUMN user_remark VARCHAR(255) NULL,
    ADD COLUMN admin_remark VARCHAR(255) NULL,
    ADD COLUMN resolution_action VARCHAR(64) NULL,
    ADD COLUMN wallet_delta INT NOT NULL DEFAULT 0,
    ADD COLUMN refund_blocking BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN processed_at TIMESTAMP NULL,
    ADD COLUMN processed_by VARCHAR(64) NULL;

UPDATE aftersale_cases
SET status = CASE
        WHEN status = 'OPEN' THEN 'PENDING'
        WHEN status = 'RESOLVED' THEN 'COMPLETED'
        ELSE status
    END,
    source = 'ADMIN_DIRECT',
    requested_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    processed_at = CASE WHEN status IN ('RESOLVED', 'COMPLETED', 'REJECTED') THEN updated_at ELSE NULL END,
    processed_by = CASE WHEN status IN ('RESOLVED', 'COMPLETED', 'REJECTED') THEN operator_name ELSE NULL END;

ALTER TABLE aftersale_cases
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_aftersale_cases_status_type_requested
    ON aftersale_cases (status, issue_type, requested_at);

CREATE INDEX idx_aftersale_cases_order_status
    ON aftersale_cases (meal_slot_order_id, status);
