-- =============================================================================
-- V26: 根治「更换骑手才能看到订单 / 派单时报数据冲突」的底层数据问题
-- =============================================================================
-- 根因：dispatch_batches 表缺少唯一键，导致同一
--       (serve_date, meal_period, rider_profile_id, area_code) 出现多条重复批次。
--       派单侧（DispatchBatchModule）与骑手端物化（RiderQueueSupport）在存在重复批次时
--       会取到不同的 batch，进而在写入 dispatch_batch_items 时撞
--       uk_dispatch_batch_items_batch_sequence(batch_id, current_sequence) 唯一键，
--       抛 DuplicateKeyException，被 GlobalExceptionHandler 转成 409「数据冲突，请刷新后重试」。
--
-- 修复分两步：
--   1) 合并存量重复批次：把重复组内所有批次项归并到最早(最小 id)的批次，并重新编号；
--   2) 新增唯一键 uk_dispatch_batches_scope，从数据库层面杜绝再次产生重复批次。
-- =============================================================================

-- 1) 给重复组中「非主批次」的批次项加一个足够大且互不相同的序号偏移，
--    避免归并到主批次时撞 uk_dispatch_batch_items_batch_sequence 唯一键。
UPDATE dispatch_batch_items dbi
JOIN (
    SELECT db.id AS dup_batch_id
    FROM dispatch_batches db
    JOIN (
        SELECT serve_date, meal_period, rider_profile_id, area_code, MIN(id) AS keep_id
        FROM dispatch_batches
        GROUP BY serve_date, meal_period, rider_profile_id, area_code
        HAVING COUNT(*) > 1
    ) grp ON db.serve_date = grp.serve_date
         AND db.meal_period = grp.meal_period
         AND db.rider_profile_id = grp.rider_profile_id
         AND db.area_code = grp.area_code
         AND db.id > grp.keep_id
) dup ON dbi.batch_id = dup.dup_batch_id
SET dbi.current_sequence = dbi.current_sequence + 1000000 + dup.dup_batch_id,
    dbi.suggested_sequence = dbi.suggested_sequence + 1000000 + dup.dup_batch_id;

-- 2) 把重复组中「非主批次」的批次项改挂到主批次（最小 id）。
UPDATE dispatch_batch_items dbi
JOIN (
    SELECT db.id AS dup_batch_id, grp.keep_id AS keep_batch_id
    FROM dispatch_batches db
    JOIN (
        SELECT serve_date, meal_period, rider_profile_id, area_code, MIN(id) AS keep_id
        FROM dispatch_batches
        GROUP BY serve_date, meal_period, rider_profile_id, area_code
        HAVING COUNT(*) > 1
    ) grp ON db.serve_date = grp.serve_date
         AND db.meal_period = grp.meal_period
         AND db.rider_profile_id = grp.rider_profile_id
         AND db.area_code = grp.area_code
         AND db.id > grp.keep_id
) dup ON dbi.batch_id = dup.dup_batch_id
SET dbi.batch_id = dup.keep_batch_id;

-- 3) 重新编号所有批次项（每个批次内 1..N 连续），消除归并后的序号空洞与偏移。
CREATE TEMPORARY TABLE tmp_batch_item_seq AS
SELECT id, ROW_NUMBER() OVER (PARTITION BY batch_id ORDER BY current_sequence, id) AS new_seq
FROM dispatch_batch_items;

UPDATE dispatch_batch_items dbi
JOIN tmp_batch_item_seq t ON dbi.id = t.id
SET dbi.current_sequence = t.new_seq,
    dbi.suggested_sequence = t.new_seq;

DROP TEMPORARY TABLE tmp_batch_item_seq;

-- 4) 删除重复（非主）批次。
DELETE db
FROM dispatch_batches db
JOIN (
    SELECT serve_date, meal_period, rider_profile_id, area_code, MIN(id) AS keep_id
    FROM dispatch_batches
    GROUP BY serve_date, meal_period, rider_profile_id, area_code
    HAVING COUNT(*) > 1
) grp ON db.serve_date = grp.serve_date
     AND db.meal_period = grp.meal_period
     AND db.rider_profile_id = grp.rider_profile_id
     AND db.area_code = grp.area_code
     AND db.id > grp.keep_id;

-- 5) 新增唯一键，从数据库层面杜绝重复批次再次产生。
ALTER TABLE `dispatch_batches`
  ADD UNIQUE KEY `uk_dispatch_batches_scope` (`serve_date`, `meal_period`, `rider_profile_id`, `area_code`);

-- 6) 刷新批次统计（total_count / delivered_count 按份数 quantity 汇总，与应用口径一致）。
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
