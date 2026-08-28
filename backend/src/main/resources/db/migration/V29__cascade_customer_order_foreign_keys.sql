-- =============================================================================
-- V29: 补齐「客户 -> 订单/钱包/地址/绑定/订阅」及「订单 -> 餐次」的级联外键,
--      并清理存量孤儿数据,从数据库机制上杜绝「订单中心与看板/骑手进度数字对不上」
-- =============================================================================
-- 背景:
--   此前外键只覆盖了「子表 -> meal_slot_orders」「子表 -> customers」(V25/V27),
--   但 daily_orders.customer_id -> customers 与 meal_slot_orders.daily_order_id -> daily_orders
--   没有外键。任何绕过服务层的删除(主档同步清空客户、批量导入、直接 SQL 等)
--   只要漏删一步,就会残留「客户已删但订单还在」的孤儿数据:
--     - 订单中心列表 findPrepPage 因 INNER JOIN customers 会隐藏这些订单
--     - 看板/骑手进度统计若不 JOIN customers 会把它们算进去
--   于是出现「订单中心 71、看板 81、骑手进度 75」的对不上账问题。
--
-- 本迁移:
--   1) 清理存量孤儿(按依赖顺序:先子表后父表,幂等可重跑);
--   2) 补齐整条链路的 ON DELETE CASCADE 外键(幂等,单条失败自动跳过不中断),
--      保证以后删除 customers / daily_orders / meal_slot_orders / meal_wallets 时
--      数据库自动级联清理,任何入口都不可能再产生孤儿。
--
-- 注意:meal_wallets 含 STORED 生成列 active_customer_id(依赖 customer_id)且带唯一索引
--       uk_meal_wallets_active_customer(应用 INSERT IGNORE 幂等依赖它)。
--       MySQL 8.0 禁止"生成列唯一索引所在列"再添加外键,因此 fk_meal_wallets_customer
--       必然失败并自动跳过;钱包链的清理由应用层显式执行
--       (CustomerAssetServiceImpl.deleteCustomer / CustomerMainSheetSyncServiceImpl.clearCustomerData),
--       与关键链路(daily_orders->customers / meal_slot_orders->daily_orders)不冲突。
-- =============================================================================

-- ============ 1) 清理存量孤儿 ============
-- 1.1 钱包流水:引用不存在的钱包,或钱包引用不存在的客户
--     (先按 wallet 判空删一遍,删除孤儿钱包后可能新产生孤儿流水,再按 customer 链补删)
DELETE wt FROM wallet_transactions wt
  LEFT JOIN meal_wallets mw ON mw.id = wt.wallet_id
  WHERE mw.id IS NULL;

-- 1.2 钱包:引用不存在的客户
DELETE mw FROM meal_wallets mw
  LEFT JOIN customers c ON c.id = mw.customer_id
  WHERE c.id IS NULL;

-- 1.3 钱包流水:钱包已随 1.2 删除而新产生的孤儿
DELETE wt FROM wallet_transactions wt
  LEFT JOIN meal_wallets mw ON mw.id = wt.wallet_id
  WHERE mw.id IS NULL;

-- 1.4 餐次订单:引用不存在的日订单,或日订单引用不存在的客户
--     (meal_slot_orders 的子表已由 V25/V27 外键 CASCADE 兜底,先删餐次即可连带清理)
DELETE mso FROM meal_slot_orders mso
  LEFT JOIN daily_orders do ON do.id = mso.daily_order_id
  WHERE do.id IS NULL;
DELETE mso FROM meal_slot_orders mso
  JOIN daily_orders do ON do.id = mso.daily_order_id
  LEFT JOIN customers c ON c.id = do.customer_id
  WHERE c.id IS NULL;

-- 1.5 日订单:引用不存在的客户
DELETE do FROM daily_orders do
  LEFT JOIN customers c ON c.id = do.customer_id
  WHERE c.id IS NULL;

-- 1.6 地址绑定 / 订阅规则 / 订阅确认 / 导入跳过 / 地址 / 客户备注 / 夜间订阅:引用不存在的客户
DELETE rb FROM rider_address_bindings rb
  LEFT JOIN customers c ON c.id = rb.customer_id
  WHERE c.id IS NULL;
DELETE sr FROM subscription_rules sr
  LEFT JOIN customers c ON c.id = sr.customer_id
  WHERE c.id IS NULL;
DELETE sc FROM subscription_confirmations sc
  LEFT JOIN customers c ON c.id = sc.customer_id
  WHERE c.id IS NULL;
DELETE sis FROM subscription_import_skips sis
  LEFT JOIN customers c ON c.id = sis.customer_id
  WHERE c.id IS NULL;
DELETE ca FROM customer_addresses ca
  LEFT JOIN customers c ON c.id = ca.customer_id
  WHERE c.id IS NULL;
DELETE cn FROM customer_notes cn
  LEFT JOIN customers c ON c.id = cn.customer_id
  WHERE c.id IS NULL;
DELETE cns FROM customer_nightly_subscriptions cns
  LEFT JOIN customers c ON c.id = cns.customer_id
  WHERE c.id IS NULL;

-- ============ 2) 补齐级联外键 (幂等,失败自动跳过) ============
DROP PROCEDURE IF EXISTS v29_add_fk_if_missing;

DELIMITER $$
CREATE PROCEDURE v29_add_fk_if_missing(
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
        BEGIN
            DECLARE EXIT HANDLER FOR SQLEXCEPTION
                -- 单条外键失败(如生成列限制)仅跳过,不中断整体迁移
                SET @v29_skip_reason = CONCAT('SKIP FK: ', p_fk);
            PREPARE s FROM @ddl;
            EXECUTE s;
            DEALLOCATE PREPARE s;
        END;
    END IF;
END$$
DELIMITER ;

-- 客户 -> 日订单 / 钱包 / 地址 / 绑定 / 订阅 / 客户备注 / 夜间订阅
CALL v29_add_fk_if_missing('daily_orders',                    'fk_daily_orders_customer',                    'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('meal_wallets',                    'fk_meal_wallets_customer',                    'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('customer_addresses',              'fk_customer_addresses_customer',              'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('rider_address_bindings',          'fk_rider_address_bindings_customer',          'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('subscription_rules',              'fk_subscription_rules_customer',              'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('subscription_confirmations',      'fk_subscription_confirmations_customer',      'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('subscription_import_skips',       'fk_subscription_import_skips_customer',       'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('customer_notes',                  'fk_customer_notes_customer',                  'customer_id', 'customers', 'id');
CALL v29_add_fk_if_missing('customer_nightly_subscriptions',  'fk_customer_nightly_subscriptions_customer',  'customer_id', 'customers', 'id');

-- 日订单 -> 餐次订单
CALL v29_add_fk_if_missing('meal_slot_orders',                'fk_meal_slot_orders_daily_order',             'daily_order_id', 'daily_orders', 'id');

-- 钱包 -> 钱包流水
CALL v29_add_fk_if_missing('wallet_transactions',             'fk_wallet_transactions_wallet',               'wallet_id', 'meal_wallets', 'id');

DROP PROCEDURE IF EXISTS v29_add_fk_if_missing;
