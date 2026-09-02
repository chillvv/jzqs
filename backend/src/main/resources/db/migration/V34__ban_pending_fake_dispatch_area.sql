-- =============================================================================
-- V34: 清除派单快照中的假区域 'PENDING'，并加 DB 约束禁止复发
-- =============================================================================
-- 事故背景（9.1 事故 503，9.2 复发）：
--   换地址时若新地址无已确认区域记忆，旧版 reconcileDispatchArea 会把
--   dispatch_assignments.area_code 写成魔法值 'PENDING'（一个不存在的区域）。
--   后果：
--   1) 骑手中心按真实区域对账少单，且多出一个不存在的 "PENDING" 区域分组；
--   2) 分单工作台「待分配」因派单行已存在而看不到这些订单（静默死锁）；
--   3) 三端（订单中心/看板/骑手中心）份数对不上。
--   代码层修复（MobileAddressModule.reconcileDispatchArea）：改为撤销派单、
--   订单回退 PENDING_DISPATCH 待商家重新归区；本迁移负责清理存量脏数据，
--   并用 CHECK 约束把「不许写假区域」固化为数据库不变量（铁律 2）。
-- =============================================================================

-- 1) 终态订单（DELIVERED 等）的派单记录必须保留：把假区域规范化为地址真实区域
--    （地址也无区域时置 ''，语义为「未知/历史遗留」，各查询路径均已排除空串）。
UPDATE dispatch_assignments da
JOIN meal_slot_orders o ON o.id = da.meal_slot_order_id
LEFT JOIN customer_addresses ca ON ca.id = o.address_id
SET da.area_code = COALESCE(NULLIF(ca.area_code, ''), '')
WHERE da.area_code = 'PENDING'
  AND o.status IN ('DELIVERED', 'CANCELLED', 'REFUNDED');

-- 2) 未终态订单：状态回退待派（与代码层 resetDispatchFlow 的语义一致）
UPDATE meal_slot_orders o
JOIN dispatch_assignments da ON da.meal_slot_order_id = o.id
SET o.status = 'PENDING_DISPATCH'
WHERE da.area_code = 'PENDING'
  AND o.status IN ('PENDING_DISPATCH', 'DISPATCHING');

-- 3) 撤销假区域派单：先删批次项，再删派单行
DELETE dbi FROM dispatch_batch_items dbi
JOIN dispatch_assignments da ON da.meal_slot_order_id = dbi.meal_slot_order_id
WHERE da.area_code = 'PENDING';

DELETE da FROM dispatch_assignments da
WHERE da.area_code = 'PENDING';

-- 4) 刷新批次统计口径（与 V24 第5步 / OrderDispatchRepository.refreshDispatchBatchMetrics 同源）
UPDATE dispatch_batches db
SET total_count = COALESCE((
        SELECT SUM(mso.quantity)
        FROM dispatch_batch_items dbi
        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
        WHERE dbi.batch_id = db.id
    ), 0),
    delivered_count = COALESCE((
        SELECT SUM(mso.quantity)
        FROM dispatch_batch_items dbi
        JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
        WHERE dbi.batch_id = db.id
          AND dbi.item_status = 'DELIVERED'
    ), 0);

-- 5) 清理删空后的孤儿批次
DELETE db FROM dispatch_batches db
WHERE NOT EXISTS (SELECT 1 FROM dispatch_batch_items dbi WHERE dbi.batch_id = db.id);

-- 6) DB 约束兜底：dispatch_assignments.area_code 永远不允许出现假区域 'PENDING'
--    （真实区域来自 dispatch_area_bindings.area_code；「待分配」语义 = 无派单行，
--      而不是派单行 + 假区域）
ALTER TABLE dispatch_assignments
    ADD CONSTRAINT chk_dispatch_assignments_area_real CHECK (area_code <> 'PENDING');
