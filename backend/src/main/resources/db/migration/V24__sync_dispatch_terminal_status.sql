-- =============================================================================
-- V24: 修复「订单真实状态」与「派单分配状态」不一致的存量脏数据
-- 背景：部分订单的 meal_slot_orders.status 已进入终态（DELIVERED/CANCELLED/REFUNDED），
--       但 dispatch_assignments.status 仍停留在活跃态（PENDING/AREA_ASSIGNED/DISPATCHING），
--       导致「更换骑手」等派单操作把已结束订单纳入派单并报「订单状态已变更，无法派单」。
-- 本迁移把存量不一致数据对齐：
--   1) 已送达订单 -> dispatch_assignments / dispatch_batch_items 同步为 DELIVERED；
--   2) 已取消/退款订单 -> 删除 dispatch_assignments / dispatch_batch_items（与取消/退款流程一致）；
--   3) 刷新受影响批次的统计（total_count / delivered_count，按份数 quantity 汇总）。
-- =============================================================================

-- 1) 已送达订单：同步派单分配记录状态为 DELIVERED
UPDATE dispatch_assignments da
JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
SET da.status = 'DELIVERED'
WHERE mso.status = 'DELIVERED'
  AND da.status <> 'DELIVERED';

-- 2) 已送达订单：同步批次项状态为 DELIVERED
UPDATE dispatch_batch_items dbi
JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
SET dbi.item_status = 'DELIVERED'
WHERE mso.status = 'DELIVERED'
  AND dbi.item_status <> 'DELIVERED';

-- 3) 已取消/退款订单：删除派单分配记录（与取消/退款流程一致）
DELETE da
FROM dispatch_assignments da
JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
WHERE mso.status IN ('CANCELLED', 'REFUNDED');

-- 4) 已取消/退款订单：删除批次项记录
DELETE dbi
FROM dispatch_batch_items dbi
JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
WHERE mso.status IN ('CANCELLED', 'REFUNDED');

-- 5) 刷新受影响批次的统计（按份数 quantity 汇总，与 RiderQueueSupport.refreshRiderBatchState 口径一致）
UPDATE dispatch_batches db
LEFT JOIN (
    SELECT dbi.batch_id,
           COALESCE(SUM(mso.quantity), 0) AS total_qty,
           COALESCE(SUM(CASE WHEN dbi.item_status = 'DELIVERED' THEN mso.quantity ELSE 0 END), 0) AS delivered_qty
    FROM dispatch_batch_items dbi
    JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
    GROUP BY dbi.batch_id
) agg ON agg.batch_id = db.id
SET db.total_count = COALESCE(agg.total_qty, 0),
    db.delivered_count = COALESCE(agg.delivered_qty, 0);
