-- =============================================================================
-- V28: 清理「订单已取消/已退款但派单/批次/回执仍残留」，保证骑手进度与订单中心一致
-- =============================================================================
-- 背景：
--   订单中心取消(CANCELLED)/退款(REFUNDED)的订单在订单中心列表不再展示,
--   但若历史遗留或某条写入路径未及时清理, dispatch_assignments /
--   dispatch_batch_items / delivery_receipts 仍会保留这些订单的记录,
--   导致「骑手中心-骑手进度」出现已删除/已退款订单、总份数/已完成被多算。
--   V25/V27 只清理了「订单已不存在」的孤儿,本迁移补上「订单已终态但子记录未清」的口径。
--
-- 本迁移：
--   1) 删除已取消/已退款订单的派单记录(dispatch_assignments);
--   2) 删除已取消/已退款订单的批次项(dispatch_batch_items),并重算受影响批次指标;
--   3) 删除已取消/已退款订单的送达回执(delivery_receipts);
--   4) 删除已无任何子项的孤立批次。
-- 与 riderProgress / findAreaOrders 的查询口径 NOT IN ('CANCELLED','REFUNDED') 保持一致。
-- =============================================================================

-- 1) 派单记录
DELETE da FROM dispatch_assignments da
  JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
  WHERE mso.status IN ('CANCELLED', 'REFUNDED');

-- 2) 批次项：先记录受影响批次,删除后重算批次指标(口径与 OrderOperationRepository.refreshDispatchBatchMetrics 一致)
DROP TEMPORARY TABLE IF EXISTS _v28_affected_batches;
CREATE TEMPORARY TABLE _v28_affected_batches AS
  SELECT DISTINCT dbi.batch_id
  FROM dispatch_batch_items dbi
  JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
  WHERE mso.status IN ('CANCELLED', 'REFUNDED');

DELETE dbi FROM dispatch_batch_items dbi
  JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
  WHERE mso.status IN ('CANCELLED', 'REFUNDED');

UPDATE dispatch_batches db
  JOIN (
      SELECT dbi.batch_id,
             COALESCE(SUM(mso.quantity), 0) AS total_count,
             COALESCE(SUM(CASE WHEN dbi.item_status = 'DELIVERED' THEN mso.quantity ELSE 0 END), 0) AS delivered_count
      FROM dispatch_batch_items dbi
      JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
      GROUP BY dbi.batch_id
  ) metrics ON metrics.batch_id = db.id
  SET db.total_count = metrics.total_count,
      db.delivered_count = metrics.delivered_count
  WHERE db.id IN (SELECT batch_id FROM _v28_affected_batches);

DROP TEMPORARY TABLE IF EXISTS _v28_affected_batches;

-- 3) 送达回执(已退款订单的回执无保留价值)
DELETE dr FROM delivery_receipts dr
  JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
  WHERE mso.status IN ('CANCELLED', 'REFUNDED');

-- 4) 孤立批次(与 V27 同一口径)
DELETE db FROM dispatch_batches db
  LEFT JOIN dispatch_batch_items dbi ON dbi.batch_id = db.id
  WHERE dbi.id IS NULL;
