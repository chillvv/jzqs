-- 数据一致性加固：为核心表补充缺失的唯一约束，消除"查后再插"并发下的重复数据。
-- 说明：customers / daily_orders / dispatch_assignments / dispatch_batch_items /
--       rider_profiles / rider_address_bindings / users 已在 baseline 中具备唯一约束，
--       本文件仅补齐两处缺口：meal_wallets（每客户至多一个生效钱包）与 delivery_receipts（每订单至多一条回执）。

-- 1) 清理 meal_wallets 中已存在的重复生效钱包（active=1）：保留最早一条，其余置为 inactive。
--    避免生成列唯一索引因历史脏数据而迁移失败。
UPDATE meal_wallets w
JOIN (
    SELECT customer_id, MIN(id) AS keep_id
    FROM meal_wallets
    WHERE active = 1
    GROUP BY customer_id
    HAVING COUNT(*) > 1
) dup ON dup.customer_id = w.customer_id AND w.id <> dup.keep_id AND w.active = 1
SET w.active = 0;

-- 2) 为 meal_wallets 增加"每客户至多一个生效钱包"的约束。
--    MySQL 不支持部分唯一索引，采用生成列 + 唯一索引：active=1 时取 customer_id，否则为 NULL。
--    NULL 在唯一索引中可重复，因此只约束 active=1 的钱包唯一。
ALTER TABLE `meal_wallets`
  ADD COLUMN `active_customer_id` bigint
  GENERATED ALWAYS AS (CASE WHEN `active` = 1 THEN `customer_id` ELSE NULL END) STORED;

ALTER TABLE `meal_wallets`
  ADD UNIQUE KEY `uk_meal_wallets_active_customer` (`active_customer_id`);

-- 3) 为 delivery_receipts 增加"每订单至多一条回执"的约束。
--    先清理已存在的重复回执（保留最早一条），再建唯一索引。
DELETE dr
FROM delivery_receipts dr
JOIN (
    SELECT meal_slot_order_id, MIN(id) AS keep_id
    FROM delivery_receipts
    GROUP BY meal_slot_order_id
    HAVING COUNT(*) > 1
) dup ON dup.meal_slot_order_id = dr.meal_slot_order_id AND dr.id <> dup.keep_id;

ALTER TABLE `delivery_receipts`
  ADD UNIQUE KEY `uk_delivery_receipts_order` (`meal_slot_order_id`);
