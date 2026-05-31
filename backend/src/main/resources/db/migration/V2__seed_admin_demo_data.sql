INSERT INTO customers (id, name, phone, source, active) VALUES
    (1, '张先生', '13800000001', 'MINIAPP', TRUE),
    (2, '李女士', '13900000002', 'MINIAPP', TRUE),
    (3, '王总', '13700000003', 'BACKEND', TRUE);

INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES
    (1, 1, '张先生', '13800000001', '高新区科技园A座8层', '高新区', TRUE),
    (2, 2, '李女士', '13900000002', '阳光小区3栋2单元', '老城区', TRUE),
    (3, 3, '王总', '13700000003', '财富中心写字楼1201', '商务区', TRUE);

INSERT INTO package_plans (id, package_code, package_name, total_meals, enabled) VALUES
    (1, 'MONTH_33', '33餐月卡套餐', 33, TRUE),
    (2, 'WEEK_7', '7餐周卡套餐', 7, TRUE);

INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES
    (1, 1, 1, 33, 1, 20, TRUE),
    (2, 2, 2, 7, 1, 5, TRUE),
    (3, 3, 1, 33, 1, 20, TRUE);

INSERT INTO wallet_transactions (id, wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES
    (1, 1, 'OPEN', 33, '系统', '开卡', TIMESTAMP '2026-05-10 08:00:00'),
    (2, 2, 'OPEN', 7, '系统', '开卡', TIMESTAMP '2026-05-10 08:00:00'),
    (3, 3, 'OPEN', 33, '系统', '开卡', TIMESTAMP '2026-05-10 08:00:00');

INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES
    (1, 1, DATE '2026-05-12', 'MINIAPP', 'DELIVERED', FALSE, TIMESTAMP '2026-05-10 09:00:00'),
    (2, 2, DATE '2026-05-12', 'MINIAPP', 'PENDING_DISPATCH', FALSE, TIMESTAMP '2026-05-10 09:05:00'),
    (3, 3, DATE '2026-05-12', 'BACKEND', 'DISPATCHING', FALSE, TIMESTAMP '2026-05-10 09:10:00');

INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES
    (1, 1, 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED'),
    (2, 2, 'DINNER', 1, 2, '-', 'PENDING_DISPATCH'),
    (3, 3, 'LUNCH', 1, 3, '微辣', 'DISPATCHING');

INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES
    (1, 1, '骑手老周', '高新区', 'DELIVERED'),
    (2, 3, '骑手小李', '商务区', 'DISPATCHING');

INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at) VALUES
    (1, 1, 'https://cos.example.com/receipt-1.jpg', '已放前台', TIMESTAMP '2026-05-10 12:00:00');

INSERT INTO notification_logs (id, customer_id, channel, template_code, payload_json, sent_at) VALUES
    (1, 1, 'WECHAT', 'DELIVERY_SUCCESS', '{"content":"订单已送达"}', TIMESTAMP '2026-05-10 12:01:00');
