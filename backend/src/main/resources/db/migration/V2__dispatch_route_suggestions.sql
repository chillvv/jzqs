
-- AI辅助区域内配送建议排序表
CREATE TABLE dispatch_route_suggestions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serve_date DATE NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    strategy_mode VARCHAR(32) NOT NULL COMMENT 'NEAR_TO_FAR / FAR_TO_NEAR',
    anchor_name VARCHAR(128) NOT NULL COMMENT '例如：五环天地',
    anchor_address VARCHAR(255) NOT NULL,
    algorithm_version VARCHAR(32) NOT NULL,
    ai_provider VARCHAR(32) NULL COMMENT '例如：deepseek',
    ai_model VARCHAR(64) NULL,
    suggestion_source VARCHAR(32) NOT NULL COMMENT 'RULE_ONLY / RULE_PLUS_AI',
    reason_summary VARCHAR(255) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suggestion_key (serve_date, meal_period, area_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI辅助区域内配送建议明细表
CREATE TABLE dispatch_route_suggestion_items (
    suggestion_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    suggested_sequence INT NOT NULL,
    base_score DECIMAL(10, 4) NOT NULL,
    adjusted_score DECIMAL(10, 4) NOT NULL,
    is_ai_adjusted BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (suggestion_id, order_id),
    INDEX idx_suggestion_items_suggestion (suggestion_id),
    INDEX idx_suggestion_items_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI辅助区域内配送反馈表
CREATE TABLE dispatch_route_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    suggestion_id BIGINT NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    serve_date DATE NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    confirmed_by VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_count INT NOT NULL,
    accepted_directly BIT NOT NULL DEFAULT 0,
    feedback_summary VARCHAR(255) NOT NULL,
    INDEX idx_feedback_key (suggestion_id),
    INDEX idx_feedback_area_date (area_code, serve_date, meal_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 区域偏好记忆表（轻量沉淀商家调整规律）
CREATE TABLE dispatch_area_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    area_code VARCHAR(64) NOT NULL,
    preference_type VARCHAR(64) NOT NULL COMMENT '例如：NEIGHBOR_ORDER, FIRST_DELIVERY',
    key_identifier VARCHAR(255) NOT NULL COMMENT '例如：小区名/楼栋号的组合',
    value_identifier VARCHAR(255) NULL,
    weight INT NOT NULL DEFAULT 1 COMMENT '调整权重，正数表示靠前，负数靠后',
    hit_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_area_preference (area_code, preference_type, key_identifier),
    INDEX idx_area_preferences (area_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
