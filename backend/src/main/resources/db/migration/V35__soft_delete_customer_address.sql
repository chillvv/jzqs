-- =============================================================================
-- V35: 客户地址软删除（停用）支持 + 存量悬空地址修复
-- =============================================================================
-- 背景：
--   删除地址原来走物理删行（MobileAddressModule / CustomerAssetServiceImpl 的
--   deleteCustomerAddress），导致 meal_slot_orders.address_id 指向不存在的地址
--   （孤儿引用）。历史订单在各端口的查询对 customer_addresses 有的是 INNER JOIN、
--   有的是 LEFT JOIN，地址被物理删除后：
--     * INNER JOIN 的端口（骑手端 RiderQueueSupport、派单中心 DispatchQueryModule、
--       顾客端 MobileCustomerQueryModule、释放列表 DeliveryReleaseSupport 等）会把
--       这些订单整体「藏」起来；
--     * LEFT JOIN 的端口（订单中心 findPrepPage，9.2 事故后改造）只显示空地址。
--   于是出现「看板 / 骑手中心 / 订单中心」三端份数对不上（与 9.2 孤儿地址事故同源）。
--
--   现改为「软删除」：
--     * 删除地址 = active=0（停用），保留地址行；
--     * 历史订单 address_id 永远指向存在的地址行，所有 INNER JOIN 端口照常显示，
--       永不产生孤儿、三端数量天然一致；
--     * 所有读「有效地址」的查询以 active = 1 为过滤条件（地址列表 / 下单选地址 /
--       默认地址 / 换地址目标 / 后台地址归属校验），软删后的地址对用户不可见、
--       也不可作为新单/换地址的目标。
--   与 V31 区域软删除同一模式。
-- =============================================================================

-- 1) 加软删除标记
ALTER TABLE customer_addresses
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=启用，0=软删除（停用）';

-- 2) 存量修复：为历史上已物理删除、导致订单 address_id 悬空的地址重建「墓碑」行（active=0）。
--    墓碑行让历史订单 INNER JOIN 仍能命中（JOIN 不按 active 过滤），三端不再藏单；
--    地址列表/下单/换地址均过滤 active=1，墓碑对用户不可见、不可复用、不占地址名额。
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

-- 3) 存量修复：订阅规则 default_address_id 若指向已删除地址，置 NULL 使其回退重新选地址，
--    避免订阅自动下单校验 active=1 时失败（墓碑 active=0 不可作为下单地址）。
UPDATE subscription_rules sr
LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id AND ca.active = TRUE
SET sr.default_address_id = NULL
WHERE sr.default_address_id IS NOT NULL AND ca.id IS NULL;
