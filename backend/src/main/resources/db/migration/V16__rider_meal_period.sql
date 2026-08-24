-- 让配送区域与骑手都按餐段（午餐/晚餐）分别管理、分别存储。

-- ===================== 骑手 rider_profiles =====================
-- 1) 新增 meal_period 列，默认晚餐（与历史数据保持一致的下发时段）
ALTER TABLE rider_profiles
    ADD COLUMN meal_period VARCHAR(16) NOT NULL DEFAULT 'DINNER';

-- 2) 唯一键从 (rider_name) 改为 (rider_name, meal_period)，
--    允许同名骑手在午餐、晚餐各自存在一条记录。
ALTER TABLE rider_profiles
    DROP INDEX uk_rider_profiles_name;

ALTER TABLE rider_profiles
    ADD UNIQUE KEY uk_rider_profiles_name (rider_name, meal_period);

-- ===================== 区域 dispatch_area_bindings =====================
-- 3) 新增 meal_period 列，默认晚餐
ALTER TABLE dispatch_area_bindings
    ADD COLUMN meal_period VARCHAR(16) NOT NULL DEFAULT 'DINNER';

-- 4) 唯一键从 (area_code) 改为 (area_code, meal_period)，
--    允许同名区域在午餐、晚餐各自存在一条绑定记录。
ALTER TABLE dispatch_area_bindings
    ADD UNIQUE KEY uk_dispatch_area_bindings_area_period (area_code, meal_period);
