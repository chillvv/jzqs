-- 将 meal_wallets 表的 package_plan_id 字段改为可空
ALTER TABLE meal_wallets MODIFY COLUMN package_plan_id BIGINT NULL;
