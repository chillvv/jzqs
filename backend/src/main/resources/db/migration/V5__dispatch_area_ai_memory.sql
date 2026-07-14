CREATE TABLE dispatch_area_ai_corrections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    area_code VARCHAR(64) NOT NULL,
    route_run_id BIGINT NULL,
    correction_mode VARCHAR(16) NOT NULL COMMENT 'CHAT / DRAG / MIXED',
    merchant_instruction TEXT NOT NULL,
    merchant_reason_summary VARCHAR(255) NOT NULL DEFAULT '',
    input_addresses_snapshot JSON NOT NULL,
    original_order_ids JSON NOT NULL,
    merchant_order_ids JSON NOT NULL,
    final_ai_order_ids JSON NOT NULL,
    ai_interpretation_summary VARCHAR(255) NOT NULL DEFAULT '',
    replan_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    replan_error VARCHAR(500) NOT NULL DEFAULT '',
    confirmed_by VARCHAR(64) NOT NULL DEFAULT 'system',
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dispatch_area_ai_corrections_area_time (area_code, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_area_ai_memories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    area_code VARCHAR(64) NOT NULL,
    memory_type VARCHAR(32) NOT NULL COMMENT 'ADDRESS_FACT / ROUTE_PREFERENCE / GROUPING_RULE / RISK_HINT',
    title VARCHAR(128) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    applicable_scene VARCHAR(32) NOT NULL DEFAULT 'ALL',
    address_keys JSON NOT NULL,
    weight INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED / DELETED',
    last_verified_at TIMESTAMP NULL,
    source_correction_ids JSON NOT NULL,
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dispatch_area_ai_memories_area_status (area_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_area_ai_memory_address_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    area_code VARCHAR(64) NOT NULL,
    normalized_address_key VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    aliases JSON NOT NULL,
    building_name VARCHAR(128) NOT NULL DEFAULT '',
    road_name VARCHAR(128) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dispatch_area_ai_memory_address_keys (area_code, normalized_address_key),
    INDEX idx_dispatch_area_ai_memory_address_keys_area (area_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
