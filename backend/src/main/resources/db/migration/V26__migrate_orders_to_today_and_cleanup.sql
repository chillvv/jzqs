-- ============================================================
-- 清理旧订单（保留今天和昨天），并去除 daily_orders 重复行
-- 注意：今天订单的复制已通过手动脚本完成，此处不再重复
-- ============================================================

-- ---- Step 1: 删除旧订单关联数据（serve_date < 2026-05-25） ----

DELETE dr FROM delivery_receipts dr
JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
JOIN daily_orders do3 ON do3.id = mso.daily_order_id
WHERE do3.serve_date < '2026-05-25';

DELETE da FROM dispatch_assignments da
JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
JOIN daily_orders do4 ON do4.id = mso.daily_order_id
WHERE do4.serve_date < '2026-05-25';

DELETE dbi FROM dispatch_batch_items dbi
JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
JOIN daily_orders do5 ON do5.id = mso.daily_order_id
WHERE do5.serve_date < '2026-05-25';

DELETE FROM meal_slot_orders
WHERE daily_order_id IN (
    SELECT id FROM (
        SELECT id FROM daily_orders WHERE serve_date < '2026-05-25'
    ) AS t
);

DELETE FROM daily_orders WHERE serve_date < '2026-05-25';

-- ---- Step 2: 清理 daily_orders 中同一客户同一天的重复行 ----

DELETE dr FROM delivery_receipts dr
JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
WHERE mso.daily_order_id IN (
    SELECT dup_id FROM (
        SELECT id AS dup_id FROM daily_orders
        WHERE (customer_id, serve_date) IN (
            SELECT customer_id, serve_date FROM daily_orders
            GROUP BY customer_id, serve_date HAVING COUNT(*) > 1
        )
        AND id NOT IN (
            SELECT MIN(id) FROM daily_orders GROUP BY customer_id, serve_date
        )
    ) AS t
);

DELETE da FROM dispatch_assignments da
JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
WHERE mso.daily_order_id IN (
    SELECT dup_id FROM (
        SELECT id AS dup_id FROM daily_orders
        WHERE (customer_id, serve_date) IN (
            SELECT customer_id, serve_date FROM daily_orders
            GROUP BY customer_id, serve_date HAVING COUNT(*) > 1
        )
        AND id NOT IN (
            SELECT MIN(id) FROM daily_orders GROUP BY customer_id, serve_date
        )
    ) AS t
);

DELETE dbi FROM dispatch_batch_items dbi
JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
WHERE mso.daily_order_id IN (
    SELECT dup_id FROM (
        SELECT id AS dup_id FROM daily_orders
        WHERE (customer_id, serve_date) IN (
            SELECT customer_id, serve_date FROM daily_orders
            GROUP BY customer_id, serve_date HAVING COUNT(*) > 1
        )
        AND id NOT IN (
            SELECT MIN(id) FROM daily_orders GROUP BY customer_id, serve_date
        )
    ) AS t
);

DELETE FROM meal_slot_orders
WHERE daily_order_id IN (
    SELECT dup_id FROM (
        SELECT id AS dup_id FROM daily_orders
        WHERE (customer_id, serve_date) IN (
            SELECT customer_id, serve_date FROM daily_orders
            GROUP BY customer_id, serve_date HAVING COUNT(*) > 1
        )
        AND id NOT IN (
            SELECT MIN(id) FROM daily_orders GROUP BY customer_id, serve_date
        )
    ) AS t
);

DELETE FROM daily_orders
WHERE (customer_id, serve_date) IN (
    SELECT customer_id, serve_date FROM (
        SELECT customer_id, serve_date FROM daily_orders
        GROUP BY customer_id, serve_date HAVING COUNT(*) > 1
    ) AS dup
)
AND id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) AS min_id FROM daily_orders GROUP BY customer_id, serve_date
    ) AS keep
);
