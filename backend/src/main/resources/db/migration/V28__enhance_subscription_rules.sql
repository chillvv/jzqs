-- 完善 subscription_rules 表，支持固定订餐时间段管理

-- 1. 新增时间戳字段
ALTER TABLE subscription_rules ADD COLUMN created_at TIMESTAMP NULL;
ALTER TABLE subscription_rules ADD COLUMN updated_at TIMESTAMP NULL;

-- 2. 为现有记录设置默认值
UPDATE subscription_rules
SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

-- 3. 为 start_date 和 end_date 为 NULL 的记录设置默认值
UPDATE subscription_rules
SET start_date = CURRENT_DATE,
    end_date = CURRENT_DATE + INTERVAL '30' DAY
WHERE start_date IS NULL OR end_date IS NULL;

-- 4. 设置字段为 NOT NULL
ALTER TABLE subscription_rules MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE subscription_rules MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 5. 添加索引优化查询性能
CREATE INDEX idx_subscription_rules_customer_dates 
ON subscription_rules(customer_id, start_date, end_date, active, paused);

CREATE INDEX idx_subscription_rules_date_range 
ON subscription_rules(start_date, end_date, active, paused);

-- 6. 添加注释
ALTER TABLE subscription_rules COMMENT = '固定订餐规则表 - 支持时间段管理';
