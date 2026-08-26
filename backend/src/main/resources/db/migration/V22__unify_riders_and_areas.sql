-- =============================================================================
-- V22: 骑手唯一化 + 区域统一
-- 背景：此前 rider_profiles / dispatch_area_bindings 按 meal_period(LUNCH/DINNER)
--       拆分，导致同一骑手存在两条档案，微信 openid 只绑定其一，登录不同步。
-- 本迁移把「骑手」「区域」的餐期维度彻底去除：
--   1) 合并 LUNCH/DINNER 双档案为单档案（保留 DINNER 主档案）；
--   2) 合并区域绑定的 LUNCH/DINNER 双记录；
--   3) 清空微信绑定与区域骑手绑定（由运营重新绑定）；
--   4) 删除 meal_period 字段并重建唯一键。
-- 注意：订单(meal_slot_orders)、派单批次(dispatch_batches)、改派记录等表的
--       meal_period 属于「订单餐期」真实业务，保持不变。
-- =============================================================================

-- Step 1: 将引用 LUNCH 档案的 rider_profile_id 重定向到同名 DINNER 主档案
UPDATE dispatch_assignments da
JOIN rider_profiles lunch  ON lunch.id = da.rider_profile_id AND lunch.meal_period = 'LUNCH'
JOIN rider_profiles dinner ON dinner.rider_name = lunch.rider_name AND dinner.meal_period = 'DINNER'
SET da.rider_profile_id = dinner.id;

UPDATE dispatch_batches db
JOIN rider_profiles lunch  ON lunch.id = db.rider_profile_id AND lunch.meal_period = 'LUNCH'
JOIN rider_profiles dinner ON dinner.rider_name = lunch.rider_name AND dinner.meal_period = 'DINNER'
SET db.rider_profile_id = dinner.id;

UPDATE delivery_exceptions de
JOIN rider_profiles lunch  ON lunch.id = de.rider_profile_id AND lunch.meal_period = 'LUNCH'
JOIN rider_profiles dinner ON dinner.rider_name = lunch.rider_name AND dinner.meal_period = 'DINNER'
SET de.rider_profile_id = dinner.id;

UPDATE rider_address_bindings rab
JOIN rider_profiles lunch  ON lunch.id = rab.rider_profile_id AND lunch.meal_period = 'LUNCH'
JOIN rider_profiles dinner ON dinner.rider_name = lunch.rider_name AND dinner.meal_period = 'DINNER'
SET rab.rider_profile_id = dinner.id;

-- Step 2: 清空微信绑定（骑手需重新登录绑定）
UPDATE rider_profiles
SET current_openid = NULL,
    wechat_open_id = NULL;

-- Step 3: 清空区域绑定的默认/备用骑手（运营重新绑定）
UPDATE dispatch_area_bindings
SET default_rider_profile_id = NULL,
    backup_rider_profile_id = NULL;

-- Step 4: 删除 LUNCH 档案（DINNER 主档案已承接全部引用）
DELETE FROM rider_profiles WHERE meal_period = 'LUNCH';

-- Step 5: 删除 LUNCH 区域绑定记录（区域统一为单记录）
DELETE FROM dispatch_area_bindings WHERE meal_period = 'LUNCH';

-- Step 6: 重建唯一键（去掉 meal_period 维度）
ALTER TABLE rider_profiles
    DROP INDEX uk_rider_profiles_name,
    ADD UNIQUE KEY uk_rider_profiles_name (rider_name);

ALTER TABLE dispatch_area_bindings
    DROP INDEX uk_dispatch_area_bindings_area_period,
    DROP INDEX idx_dispatch_area_bindings_area_code,
    ADD UNIQUE KEY uk_dispatch_area_bindings_area (area_code);

-- Step 7: 删除 meal_period 字段
ALTER TABLE rider_profiles DROP COLUMN meal_period;
ALTER TABLE dispatch_area_bindings DROP COLUMN meal_period;
