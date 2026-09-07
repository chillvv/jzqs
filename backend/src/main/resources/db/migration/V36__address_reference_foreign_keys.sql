-- =============================================================================
-- V36: 地址引用完整性外键（禁止物理删除仍被订单引用的收货地址）
-- =============================================================================
-- 背景:
--   customer_addresses.id 此前没有被任何外键引用。物理 DELETE 地址不会报错、
--   也不会级联任何东西，只会静默留下孤儿引用:
--     * meal_slot_orders.address_id 是 NOT NULL 列，指向不存在的地址行后，
--       骑手端 RiderQueueSupport / 派单中心 DispatchQueryModule / 顾客端
--       MobileCustomerQueryModule / 释放列表 DeliveryReleaseSupport 等
--       INNER JOIN customer_addresses 的端口会把这些订单整体「藏」起来
--       （9.2 事故，详见 V35 文件头说明）。
--     * subscription_confirmations.address_id /
--       subscription_rules.default_address_id 同理悬空。
--
--   V35 已把「删除地址」在应用层改为软删除（active=0），消除了孤儿来源；
--   本次在数据库层加硬保证：任何绕过服务层的直接 SQL / 手工运维
--   都不可能再制造孤儿地址引用。
--
-- 策略（按数据重要性分级）:
--   meal_slot_orders.address_id                    ON DELETE RESTRICT  有订单引用 → 禁止删除
--   subscription_confirmations.address_id          ON DELETE RESTRICT  有确认单引用 → 禁止删除
--   subscription_rules.default_address_id          ON DELETE SET NULL  地址真被删 → 回退重新选地址
--   rider_address_bindings.address_id              ON DELETE CASCADE   区域记忆随地址一起清
--   address_reference_images.customer_address_id   ON DELETE CASCADE   门牌参考图随地址一起清
--
-- 注意:
--   1) RESTRICT 只拦「仍被引用」的地址；零引用的停用地址（墓碑）仍可物理删除，
--      回收口径见 docs 中的地址重置操作手册。
--   2) 删除客户的路径（CustomerAssetServiceImpl.deleteCustomer /
--      CustomerMainSheetSyncServiceImpl.clearCustomerData）都是
--      「先删 meal_slot_orders 再删 customer_addresses」，顺序天然满足 RESTRICT。
--   3) 本脚本幂等，重复执行不报错。
-- =============================================================================

-- ============ 1) 存量孤儿兜底修复（幂等，其他环境可能仍有 V35 之前的孤儿） ============

-- 1.1 为仍悬空的订单地址重建「墓碑」行（active=0），保证 INNER JOIN 端口不丢单。
--     与 V35 第 2 步同口径，重复执行时因 address_id 已存在而不会重复插入。
INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default, active)
SELECT
    mso.address_id,
    do.customer_id,
    COALESCE(c.name, ''),
    COALESCE(c.phone, ''),
    '（历史地址已删除）',
    '',
    0,
    0
FROM meal_slot_orders mso
JOIN daily_orders do ON do.id = mso.daily_order_id
JOIN customers c ON c.id = do.customer_id
WHERE mso.address_id NOT IN (SELECT id FROM customer_addresses)
GROUP BY mso.address_id, do.customer_id, c.name, c.phone;

-- 1.2 订阅确认单：指向已不存在地址的置 NULL（确认单是当日 transient 数据，
--     宁可丢地址也不能留悬空引用；订阅自动下单会要求重新选地址）。
UPDATE subscription_confirmations sc
LEFT JOIN customer_addresses ca ON ca.id = sc.address_id
SET sc.address_id = NULL
WHERE sc.address_id IS NOT NULL AND ca.id IS NULL;

-- 1.3 订阅规则：默认地址指向已删除地址时置 NULL，回退到「先加地址」分支。
UPDATE subscription_rules sr
LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
SET sr.default_address_id = NULL
WHERE sr.default_address_id IS NOT NULL AND ca.id IS NULL;

-- 1.4 骑手区域记忆 / 门牌参考图：孤儿行直接清理（无历史价值）。
DELETE rb FROM rider_address_bindings rb
  LEFT JOIN customer_addresses ca ON ca.id = rb.address_id
  WHERE ca.id IS NULL;

DELETE ari FROM address_reference_images ari
  LEFT JOIN customer_addresses ca ON ca.id = ari.customer_address_id
  WHERE ca.id IS NULL;

-- ============ 2) 添加外键（幂等，已存在则跳过） ============
DROP PROCEDURE IF EXISTS v36_add_fk_if_missing;

DELIMITER $$
CREATE PROCEDURE v36_add_fk_if_missing(
    IN p_table VARCHAR(64), IN p_fk VARCHAR(64),
    IN p_cols VARCHAR(255), IN p_ref_table VARCHAR(64), IN p_ref_cols VARCHAR(255),
    IN p_on_delete VARCHAR(32)
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
            ' ON DELETE ', p_on_delete, ' ON UPDATE CASCADE'
        );
        PREPARE s FROM @ddl;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$
DELIMITER ;

-- 订单 / 订阅确认单：仍被引用则禁止物理删除地址
CALL v36_add_fk_if_missing('meal_slot_orders',           'fk_meal_slot_orders_address',           'address_id',           'customer_addresses', 'id', 'RESTRICT');
CALL v36_add_fk_if_missing('subscription_confirmations', 'fk_subscription_confirmations_address', 'address_id',           'customer_addresses', 'id', 'RESTRICT');

-- 订阅规则默认地址：地址真被删则自动置空，回退重新选地址
CALL v36_add_fk_if_missing('subscription_rules',         'fk_subscription_rules_default_address', 'default_address_id',   'customer_addresses', 'id', 'SET NULL');

-- 区域记忆 / 门牌参考图：随地址一起清
CALL v36_add_fk_if_missing('rider_address_bindings',     'fk_rider_address_bindings_address',     'address_id',           'customer_addresses', 'id', 'CASCADE');
CALL v36_add_fk_if_missing('address_reference_images',   'fk_address_reference_images_address',   'customer_address_id',  'customer_addresses', 'id', 'CASCADE');

DROP PROCEDURE IF EXISTS v36_add_fk_if_missing;
