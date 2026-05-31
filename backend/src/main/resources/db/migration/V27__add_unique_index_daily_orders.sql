-- 给 daily_orders 加唯一索引：同一客户同一天只能有一条主订单
-- （午餐/晚餐是 meal_slot_orders 里的两条，通过 meal_period 区分）
ALTER TABLE daily_orders
    ADD CONSTRAINT uk_daily_orders_customer_date UNIQUE (customer_id, serve_date);
