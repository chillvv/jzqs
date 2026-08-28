-- =============================================================================
-- V27: 清理「订单已删但派单/批次/回执/备注/售后」孤儿，确保骑手进度统计不再残留
-- =============================================================================
-- 背景：
--   测试期间管理员硬删 meal_slot_orders / daily_orders 时,若应用层 dispatch
--   清理顺序中任何一步失败,都会让 dispatch_assignments / dispatch_batch_items /
--   delivery_receipts / order_notes / aftersale_cases 留下指向已删除订单的孤儿行。
--   V25 已经加了 ON DELETE CASCADE 外键兜底,但存量的孤儿行不会被外键反向清理。
--   此外,如果某些环境 V25 没有运行过(迁移历史不一致),V25 的约束也不存在。
--
-- 影响：
--   dispatch_assignments 中的孤儿会让「骑手进度」riderProgress 接口出现
--   "已送达份数 / 总份数" 与实际 binding.orders 不一致;同时也会让"今日送达"
--   看板多算。
--
-- 本迁移：
--   1) 一次性清理所有子表孤儿行(用 LEFT JOIN ... IS NULL 的标准巡检口径);
--   2) 把 dispatch_assignments 上缺失的 ON DELETE CASCADE 外键补上(幂等),
--      保证后续删除 meal_slot_orders / customers 时数据库兜底清理。
-- =============================================================================

-- 1) 子表孤儿清理 (与 init-test-db.sh、e2e 巡检口径一致)
DELETE da FROM dispatch_assignments da
  LEFT JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
  WHERE mso.id IS NULL;

DELETE dbi FROM dispatch_batch_items dbi
  LEFT JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
  WHERE mso.id IS NULL;

DELETE dr FROM delivery_receipts dr
  LEFT JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
  WHERE mso.id IS NULL;

DELETE on_ FROM order_notes on_
  LEFT JOIN meal_slot_orders mso ON mso.id = on_.meal_slot_order_id
  WHERE mso.id IS NULL;

DELETE cd FROM customer_delivery_subscriptions cd
  LEFT JOIN meal_slot_orders mso ON mso.id = cd.meal_slot_order_id
  WHERE mso.id IS NULL;

DELETE ac FROM aftersale_cases ac
  LEFT JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
  WHERE mso.id IS NULL;

-- 2) 兜底加外键 (幂等)。MySQL 8 不支持 ON DELETE CASCADE 改写,所以先查后建。
--    若 V25 已生效,以下判断会让所有 IF 分支走 SELECT,无副作用。

DROP PROCEDURE IF EXISTS v27_add_fk_if_missing;

DELIMITER $$
CREATE PROCEDURE v27_add_fk_if_missing(
    IN p_table VARCHAR(64), IN p_fk VARCHAR(64),
    IN p_cols VARCHAR(255), IN p_ref_table VARCHAR(64), IN p_ref_cols VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
          AND CONSTRAINT_NAME = p_fk AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE ', p_table,
            ' ADD CONSTRAINT ', p_fk,
            ' FOREIGN KEY (', p_cols, ') REFERENCES ', p_ref_table, ' (', p_ref_cols, ')',
            ' ON DELETE CASCADE ON UPDATE CASCADE'
        );
        PREPARE s FROM @ddl;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$
DELIMITER ;

CALL v27_add_fk_if_missing('dispatch_assignments',            'fk_dispatch_assignments_order', 'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL v27_add_fk_if_missing('dispatch_batch_items',            'fk_dispatch_batch_items_order', 'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL v27_add_fk_if_missing('delivery_receipts',               'fk_delivery_receipts_order',   'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL v27_add_fk_if_missing('order_notes',                     'fk_order_notes_order',        'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL v27_add_fk_if_missing('aftersale_cases',                 'fk_aftersale_cases_order',    'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL v27_add_fk_if_missing('customer_delivery_subscriptions', 'fk_customer_delivery_subscriptions_order', 'meal_slot_order_id', 'meal_slot_orders', 'id');

DROP PROCEDURE IF EXISTS v27_add_fk_if_missing;

-- 3) 兜底删除 dispatch_batches 中已无任何子项的孤立批次
DELETE db FROM dispatch_batches db
  LEFT JOIN dispatch_batch_items dbi ON dbi.batch_id = db.id
  WHERE dbi.id IS NULL;
