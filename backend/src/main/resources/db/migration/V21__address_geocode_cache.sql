-- 地址地理编码缓存表
CREATE TABLE address_geocode_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    address VARCHAR(500) NOT NULL COMMENT '完整地址',
    latitude DOUBLE NOT NULL COMMENT '纬度',
    longitude DOUBLE NOT NULL COMMENT '经度',
    formatted_address VARCHAR(500) COMMENT '格式化地址',
    province VARCHAR(50) COMMENT '省份',
    city VARCHAR(50) COMMENT '城市',
    district VARCHAR(50) COMMENT '区县',
    confidence INT COMMENT '置信度(0-100)',
    geocode_source VARCHAR(20) DEFAULT 'TENCENT' COMMENT '地理编码来源',
    hit_count INT DEFAULT 0 COMMENT '命中次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_address (address),
    KEY idx_updated_at (updated_at),
    KEY idx_hit_count (hit_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地址地理编码缓存表';

-- 骑手配送异常记录表
CREATE TABLE delivery_exceptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meal_slot_order_id BIGINT NOT NULL COMMENT '订单ID',
    rider_profile_id BIGINT NOT NULL COMMENT '骑手ID',
    rider_name VARCHAR(64) NOT NULL COMMENT '骑手姓名',
    exception_type VARCHAR(32) NOT NULL COMMENT '异常类型',
    exception_note TEXT COMMENT '异常说明',
    customer_phone VARCHAR(32) COMMENT '客户电话',
    delivery_address VARCHAR(500) COMMENT '配送地址',
    exception_images TEXT COMMENT '异常图片(JSON数组)',
    resolved BOOLEAN DEFAULT FALSE COMMENT '是否已解决',
    resolved_at TIMESTAMP NULL COMMENT '解决时间',
    resolved_by VARCHAR(64) COMMENT '解决人',
    resolution_note TEXT COMMENT '解决说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order (meal_slot_order_id),
    KEY idx_rider (rider_profile_id),
    KEY idx_resolved (resolved),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送异常记录表';

-- 骑手配送轨迹表（可选，用于统计分析）
CREATE TABLE rider_delivery_tracks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rider_profile_id BIGINT NOT NULL COMMENT '骑手ID',
    meal_slot_order_id BIGINT NOT NULL COMMENT '订单ID',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型',
    action_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
    latitude DOUBLE COMMENT '纬度',
    longitude DOUBLE COMMENT '经度',
    note VARCHAR(255) COMMENT '备注',
    KEY idx_rider_order (rider_profile_id, meal_slot_order_id),
    KEY idx_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手配送轨迹表';
