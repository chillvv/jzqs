-- ============================================================================
-- 订单子表级联外键约束
-- ============================================================================
-- 背景：aftersale_cases / delivery_receipts / dispatch_assignments /
--       dispatch_batch_items / order_notes / customer_delivery_subscriptions
--       这几张表通过 meal_slot_order_id 关联 meal_slot_orders，但库上没有外键约束。
--       删除订单时若应用层漏清，会残留"孤儿记录"，污染看板统计
--       （如"待处理售后"出现点不开的幻数、"今日送达"多算）。
--
--       本次在数据库层面加硬保证：删除 meal_slot_orders / customers
--       时由数据库自动级联清理子表，彻底杜绝应用层绕过服务层写删除 SQL 造成的孤儿问题。
--
-- 约定：所有外键列类型均为 bigint，与所引用主键一致。
--       本脚本幂等：重复执行不会报错（通过 information_schema 判断是否存在）。
-- ============================================================================

-- 前置清理：删除子表中引用了不存在订单/客户的孤儿记录（否则加外键会失败）。
-- 与 backend/scripts/init-test-db.sh 的孤儿清理口径一致；对生产库同样安全
-- （这些记录本就是脏数据，V24 已处理订单侧，此处兜底子表侧）。
DELETE da FROM dispatch_assignments da
  LEFT JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE dbi FROM dispatch_batch_items dbi
  LEFT JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE dr FROM delivery_receipts dr
  LEFT JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE ac FROM aftersale_cases ac
  LEFT JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE ac FROM aftersale_cases ac
  LEFT JOIN customers c ON c.id = ac.customer_id
  WHERE c.id IS NULL;
DELETE cs FROM customer_delivery_subscriptions cs
  LEFT JOIN meal_slot_orders mso ON mso.id = cs.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE cs FROM customer_delivery_subscriptions cs
  LEFT JOIN customers c ON c.id = cs.customer_id
  WHERE c.id IS NULL;
DELETE on_ FROM order_notes on_
  LEFT JOIN meal_slot_orders mso ON mso.id = on_.meal_slot_order_id
  WHERE mso.id IS NULL;
DELETE on_ FROM order_notes on_
  LEFT JOIN customers c ON c.id = on_.customer_id
  WHERE c.id IS NULL;

-- aftersale_cases 尚无主键，先补上（加外键需要被引用表/引用表有确定性行标识）
-- 兼容已存在 id 列的环境：若存在则跳过
SET @has_id := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aftersale_cases' AND COLUMN_NAME = 'id');
SET @sql_id := IF(@has_id = 0,
    'ALTER TABLE aftersale_cases ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (id)',
    'SELECT 1');
PREPARE stmt FROM @sql_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 幂等添加外键的辅助逻辑：逐表判断约束不存在时才添加
DROP PROCEDURE IF EXISTS add_fk_if_missing;
DELIMITER $$
CREATE PROCEDURE add_fk_if_missing(
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

CALL add_fk_if_missing('aftersale_cases',                 'fk_aftersale_cases_order',                    'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('aftersale_cases',                 'fk_aftersale_cases_customer',                 'customer_id',        'customers',        'id');
CALL add_fk_if_missing('delivery_receipts',               'fk_delivery_receipts_order',                  'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('dispatch_assignments',            'fk_dispatch_assignments_order',               'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('dispatch_batch_items',            'fk_dispatch_batch_items_order',               'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('order_notes',                     'fk_order_notes_order',                        'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('order_notes',                     'fk_order_notes_customer',                     'customer_id',        'customers',        'id');
CALL add_fk_if_missing('customer_delivery_subscriptions', 'fk_customer_delivery_subscriptions_order',    'meal_slot_order_id', 'meal_slot_orders', 'id');
CALL add_fk_if_missing('customer_delivery_subscriptions', 'fk_customer_delivery_subscriptions_customer', 'customer_id',        'customers',        'id');

DROP PROCEDURE IF EXISTS add_fk_if_missing;
