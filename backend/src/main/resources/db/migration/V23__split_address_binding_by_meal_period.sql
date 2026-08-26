-- V23: 把「地址 -> 区域」的派单记忆按午餐/晚餐完全分开。
-- 之前 rider_address_bindings 的唯一键是 (customer_id, address_id)，同一地址的
-- 午餐、晚餐共享一条记忆。现在改为 (customer_id, address_id, meal_period)，
-- 让午餐记忆与晚餐记忆各自独立（上次午餐分给哪个区域，这次午餐仍分给哪个区域）。
--
-- 设计说明：
-- - meal_period 允许为 NULL，表示「通用/历史记忆」。
--   写入时总是带上具体餐期；读取时优先匹配同餐期记忆，无同餐期时回退到
--   通用(NULL)记忆，避免历史数据失效。
-- - 历史数据 meal_period 统一置为 NULL（通用），由后续实际派单自然按餐期刷新。

ALTER TABLE rider_address_bindings
    ADD COLUMN meal_period varchar(16) DEFAULT NULL
        COMMENT '餐段记忆键：LUNCH/DINNER；NULL 表示通用历史记忆';

-- 旧唯一键不含 meal_period，先删除再重建为三元唯一键。
ALTER TABLE rider_address_bindings
    DROP INDEX uk_rider_address_bindings_customer_address;

ALTER TABLE rider_address_bindings
    ADD UNIQUE KEY uk_rider_address_bindings_customer_address (
        customer_id, address_id, meal_period
    );

-- 历史行统一标记为通用记忆（NULL），保持兼容性。
UPDATE rider_address_bindings
SET meal_period = NULL
WHERE meal_period IS NULL;
