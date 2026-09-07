-- =============================================================================
-- V37: 固定用餐默认地址与客户地址簿的一致性护栏（触发器）
-- =============================================================================
-- 背景:
--   subscription_rules.default_address_id 此前只受「地址行存在」的外键约束
--   （V36, ON DELETE SET NULL / RESTRICT），但无法约束两件事:
--     1) 指向的地址必须 active=1 —— 地址被停用（软删除）后，固定用餐仍显示
--        一个用户端已看不到的地址（"明明已经删了还有"）；
--     2) 指向的地址必须属于规则所属客户本人。
--   应用层删除地址时虽会同步置空规则（MobileAddressModule /
--   CustomerAssetServiceImpl），但绕过服务层的直接 SQL（批量重置、手工运维）
--   不会同步，一致性靠人肉保证。
--
-- 本迁移用一对触发器把不变量落进数据库:
--   1) trg_subscription_rules_addr_guard / _upd
--      写入 subscription_rules 时校验 default_address_id 必须是
--      「该客户本人 + active=1」的地址，否则 SIGNAL 报错拒绝；
--   2) trg_customer_addresses_deactivate_sync
--      地址 active 由 1 -> 0（停用）时，自动:
--        - 置空引用它的 subscription_rules.default_address_id
--        - 清理 rider_address_bindings（区域记忆）
--        - 清理 address_reference_images（门牌参考图）
--      与应用层 deleteCustomerAddress 的行为完全一致，任何路径都同步。
--
-- 兼容性:
--   - 置 NULL 不受触发器影响（订阅规则清空默认地址仍随时可做）；
--   - 地址行硬删除仍由 V36 外键兜底（RESTRICT / SET NULL / CASCADE）；
--   - 触发器只在 active 1->0 边沿触发，普通地址编辑零开销。
-- =============================================================================

DELIMITER $$

DROP TRIGGER IF EXISTS trg_subscription_rules_addr_guard$$
-- 1) 固定用餐默认地址守卫：新增规则时校验
CREATE TRIGGER trg_subscription_rules_addr_guard
BEFORE INSERT ON subscription_rules
FOR EACH ROW
BEGIN
    IF NEW.default_address_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM customer_addresses ca
            WHERE ca.id = NEW.default_address_id
              AND ca.customer_id = NEW.customer_id
              AND ca.active = 1
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '固定用餐默认地址必须为该客户本人且处于生效状态的地址';
        END IF;
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_subscription_rules_addr_guard_upd$$
-- 2) 固定用餐默认地址守卫：更新规则时校验
CREATE TRIGGER trg_subscription_rules_addr_guard_upd
BEFORE UPDATE ON subscription_rules
FOR EACH ROW
BEGIN
    IF NEW.default_address_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM customer_addresses ca
            WHERE ca.id = NEW.default_address_id
              AND ca.customer_id = NEW.customer_id
              AND ca.active = 1
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '固定用餐默认地址必须为该客户本人且处于生效状态的地址';
        END IF;
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_customer_addresses_deactivate_sync$$
-- 3) 地址停用时自动同步关联表（与应用层 deleteCustomerAddress 行为一致）
CREATE TRIGGER trg_customer_addresses_deactivate_sync
BEFORE UPDATE ON customer_addresses
FOR EACH ROW
BEGIN
    IF OLD.active = 1 AND NEW.active = 0 THEN
        UPDATE subscription_rules SET default_address_id = NULL
        WHERE default_address_id = OLD.id;
        DELETE FROM rider_address_bindings WHERE address_id = OLD.id;
        DELETE FROM address_reference_images WHERE customer_address_id = OLD.id;
    END IF;
END$$

DELIMITER ;
