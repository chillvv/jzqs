-- =============================================================================
-- jzqs 上线前一致性巡检(只读,可直连生产库执行)
-- =============================================================================
-- 用途:发版前扫描四类曾在生产环境真实出现过事故的脏数据:
--         一、重复记录        二、跨表状态不一致
--         三、悬空外键(孤儿)  四、空值语义异常
-- 用法:
--   mysql -h <host> -P <port> -u <user> -p --default-character-set=utf8mb4 jzqs \
--     < scripts/consistency-check.sql
-- 判定:所有查询结果为空 = 通过。任何一条查询返回行,即按 check_id 对照注释
--   定位事故类型,处理完毕(或确认可接受)后再发版。
-- 性能:全部为索引扫描或小表聚合,只读不锁业务写。
-- 注:每条检查的注释标明「为什么查这个」——对应哪次事故(V20~V30 迁移)
--   或哪条业务不变量。状态口径与后端代码一致:
--   订单终态 = DELIVERED / CANCELLED / REFUNDED(OrderStatus.java);
--   派单分配活跃态 = PENDING / AREA_ASSIGNED / DISPATCHING
--   (DispatchAssignmentModule.java:216,全库仅这四个值)。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 一、重复记录:唯一约束兜底后的巡检,确认约束未被绕过
--    (唯一索引挡得住应用层并发,挡不住直连写库/迁移回滚/手工修复)
-- =============================================================================

-- 1.1 meal_wallets:每客户至多一个生效(active=1)钱包
-- 为什么:V20 之前无唯一约束,「查后再插」并发产生过一客户多个生效钱包,
--   余额展示与扣减随机命中其一。V20 已加生成列唯一索引
--   uk_meal_wallets_active_customer 兜底;本条确认历史数据清理后无新增重复
--   (直连写库仍可绕过索引制造重复)。
SELECT '1.1' AS check_id,
       '重复生效钱包' AS issue,
       w.customer_id,
       COUNT(*) AS active_wallet_count,
       GROUP_CONCAT(w.id ORDER BY w.id) AS active_wallet_ids
FROM meal_wallets w
WHERE w.active = 1
GROUP BY w.customer_id
HAVING COUNT(*) > 1;

-- 1.2 subscription_rules:每客户至多一行
-- 为什么:V21 之前无唯一约束。所有读取路径(selectOne(customer_id)、
--   各报表 MAX(id) 子查询)都假设每客户一行,一客户多行会令移动端订阅读写
--   直接抛 TooManyResults 异常。V21 已加 uk_subscription_rules_customer 兜底。
SELECT '1.2' AS check_id,
       '重复订阅规则' AS issue,
       sr.customer_id,
       COUNT(*) AS rule_count,
       GROUP_CONCAT(sr.id ORDER BY sr.id) AS rule_ids
FROM subscription_rules sr
GROUP BY sr.customer_id
HAVING COUNT(*) > 1;

-- 1.3 delivery_receipts:每订单至多一条回执
-- 为什么:V20 曾清理过重复回执并加 uk_delivery_receipts_order;
--   重复回执会让顾客端「送达凭证」重复展示、云存储清理任务重复计费。
SELECT '1.3' AS check_id,
       '重复送达回执' AS issue,
       dr.meal_slot_order_id,
       COUNT(*) AS receipt_count,
       GROUP_CONCAT(dr.id ORDER BY dr.id) AS receipt_ids
FROM delivery_receipts dr
GROUP BY dr.meal_slot_order_id
HAVING COUNT(*) > 1;

-- 1.4 order_notes:同订单同类型同来源同内容重复
-- 为什么:V30 把错放进「用户备注」的 SUBSCRIPTION_DEFAULT 行改判为商家备注时
--   曾产生重复;订单备注接口按行返回,重复行 = 三端重复展示同一条备注。
SELECT '1.4' AS check_id,
       '重复订单备注' AS issue,
       on_.meal_slot_order_id,
       on_.note_type,
       on_.source_type,
       LEFT(on_.content, 40) AS content_preview,
       COUNT(*) AS dup_count,
       GROUP_CONCAT(on_.id ORDER BY on_.id) AS dup_ids
FROM order_notes on_
GROUP BY on_.meal_slot_order_id, on_.note_type, on_.source_type, on_.content
HAVING COUNT(*) > 1;

-- =============================================================================
-- 二、跨表状态不一致:订单真实状态 vs 派单/批次侧状态
-- =============================================================================

-- 2.1 终态订单仍挂在活跃派单分配上
-- 为什么:V24 事故——订单已进入终态但 dispatch_assignments 停留在活跃态,
--   导致「更换骑手」把已结束订单纳入派单并报「订单状态已变更,无法派单」,
--   骑手看板份数也多算。活跃态口径与 DispatchAssignmentModule.java:216 一致;
--   另防御性纳入历史库出现过的骑手态(现版本代码已不再写入)。
SELECT '2.1' AS check_id,
       '终态订单挂活跃派单' AS issue,
       o.id AS order_id,
       o.status AS order_status,
       da.id AS assignment_id,
       da.status AS assignment_status,
       da.area_code,
       da.rider_name
FROM meal_slot_orders o
JOIN dispatch_assignments da ON da.meal_slot_order_id = o.id
WHERE o.status IN ('DELIVERED', 'CANCELLED', 'REFUNDED')
  AND da.status IN ('PENDING', 'AREA_ASSIGNED', 'DISPATCHING',
                    'ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'IN_TRANSIT', 'DISPATCHED');

-- 2.2 已取消/退款订单仍残留派单分配或批次项
-- 为什么:取消/退款流程要求删净派单分配与批次项(V24 第3/4步、V28 事故);
--   残留会让批次 total_count 虚高、骑手「今日待送」出现幽灵订单。
SELECT '2.2a' AS check_id,
       '取消/退款订单残留派单分配' AS issue,
       da.id AS assignment_id,
       da.meal_slot_order_id AS order_id,
       o.status AS order_status,
       da.status AS assignment_status
FROM dispatch_assignments da
JOIN meal_slot_orders o ON o.id = da.meal_slot_order_id
WHERE o.status IN ('CANCELLED', 'REFUNDED');

SELECT '2.2b' AS check_id,
       '取消/退款订单残留批次项' AS issue,
       dbi.id AS batch_item_id,
       dbi.batch_id,
       dbi.meal_slot_order_id AS order_id,
       o.status AS order_status,
       dbi.item_status
FROM dispatch_batch_items dbi
JOIN meal_slot_orders o ON o.id = dbi.meal_slot_order_id
WHERE o.status IN ('CANCELLED', 'REFUNDED');

-- 2.3 已送达订单的批次项仍非 DELIVERED
-- 为什么:V24 第2步按此对齐过存量;不一致会让批次 delivered_count 偏低、
--   骑手进度条走不完、批次永远到不了 FINISHED。
SELECT '2.3' AS check_id,
       '送达订单批次项未同步' AS issue,
       dbi.id AS batch_item_id,
       dbi.batch_id,
       dbi.meal_slot_order_id AS order_id,
       o.status AS order_status,
       dbi.item_status
FROM dispatch_batch_items dbi
JOIN meal_slot_orders o ON o.id = dbi.meal_slot_order_id
WHERE o.status = 'DELIVERED'
  AND dbi.item_status <> 'DELIVERED';

-- 2.4 批次统计与批次项实际汇总不一致
-- 为什么:V24 第5步的刷新口径(total/delivered 按份数 quantity 汇总,
--   与 RiderQueueSupport.refreshRiderBatchState 同源)。统计漂移会让
--   骑手端进度与看板份数对不上,无法判断批次是否真正完成。
SELECT '2.4' AS check_id,
       '批次统计漂移' AS issue,
       db.id AS batch_id,
       db.batch_status,
       db.total_count AS stored_total,
       agg.total_qty AS actual_total,
       db.delivered_count AS stored_delivered,
       agg.delivered_qty AS actual_delivered
FROM dispatch_batches db
JOIN (
    SELECT dbi.batch_id,
           COALESCE(SUM(mso.quantity), 0) AS total_qty,
           COALESCE(SUM(CASE WHEN dbi.item_status = 'DELIVERED'
                             THEN mso.quantity ELSE 0 END), 0) AS delivered_qty
    FROM dispatch_batch_items dbi
    JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
    GROUP BY dbi.batch_id
) agg ON agg.batch_id = db.id
WHERE db.total_count <> agg.total_qty
   OR db.delivered_count <> agg.delivered_qty;

-- =============================================================================
-- 三、悬空外键:子表指向已不存在的父行(孤儿记录)
--    V25/V27/V29 已补 ON DELETE CASCADE 外键兜底;但迁移历史不完整的环境、
--    或任何绕过外键的写法仍可能再产生孤儿,故发版前巡检保留。
-- =============================================================================

-- 3.1~3.3 为什么:V27 事故——管理员硬删订单时应用层清理链任一步失败,
--   六张订单子表留下孤儿行,骑手进度接口「已送达/总份数」与实际绑定订单
--   不一致,「今日送达」看板多算;V29 事故——客户已删但订单还在,出现
--   「订单中心 71、看板 81、骑手进度 75」的对不上账。

-- 3.1 dispatch_assignments → meal_slot_orders(骑手进度/看板份数来源)
SELECT '3.1' AS check_id,
       '派单分配指向已删订单' AS issue,
       da.id AS row_id,
       da.meal_slot_order_id AS missing_parent_id
FROM dispatch_assignments da
LEFT JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.2 dispatch_batch_items → meal_slot_orders(批次份数统计来源)
SELECT '3.2' AS check_id,
       '批次项指向已删订单' AS issue,
       dbi.id AS row_id,
       dbi.meal_slot_order_id AS missing_parent_id
FROM dispatch_batch_items dbi
LEFT JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.3 delivery_receipts → meal_slot_orders(回执展示与云存储清理依据)
SELECT '3.3' AS check_id,
       '送达回执指向已删订单' AS issue,
       dr.id AS row_id,
       dr.meal_slot_order_id AS missing_parent_id
FROM delivery_receipts dr
LEFT JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.4 order_notes → meal_slot_orders(三端备注展示)
SELECT '3.4' AS check_id,
       '订单备注指向已删订单' AS issue,
       on_.id AS row_id,
       on_.meal_slot_order_id AS missing_parent_id
FROM order_notes on_
LEFT JOIN meal_slot_orders mso ON mso.id = on_.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.5 aftersale_cases → meal_slot_orders(售后工单列表点开的订单)
SELECT '3.5' AS check_id,
       '售后工单指向已删订单' AS issue,
       ac.id AS row_id,
       ac.meal_slot_order_id AS missing_parent_id
FROM aftersale_cases ac
LEFT JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.6 customer_delivery_subscriptions → meal_slot_orders(送达订阅)
SELECT '3.6' AS check_id,
       '送达订阅指向已删订单' AS issue,
       cds.id AS row_id,
       cds.meal_slot_order_id AS missing_parent_id
FROM customer_delivery_subscriptions cds
LEFT JOIN meal_slot_orders mso ON mso.id = cds.meal_slot_order_id
WHERE mso.id IS NULL;

-- 3.7 meal_slot_orders → daily_orders(餐次订单挂在已删的日订单下)
SELECT '3.7' AS check_id,
       '餐次订单指向已删日订单' AS issue,
       mso.id AS row_id,
       mso.daily_order_id AS missing_parent_id
FROM meal_slot_orders mso
LEFT JOIN daily_orders do_ ON do_.id = mso.daily_order_id
WHERE do_.id IS NULL;

-- 3.8 daily_orders → customers(订单中心 INNER JOIN customers 会隐藏这些订单,
--     看板/骑手进度却会算进去——V29 对不上账事故的直接成因)
SELECT '3.8' AS check_id,
       '日订单指向已删客户' AS issue,
       do_.id AS row_id,
       do_.customer_id AS missing_parent_id
FROM daily_orders do_
LEFT JOIN customers c ON c.id = do_.customer_id
WHERE c.id IS NULL;

-- 3.9 wallet_transactions → meal_wallets(孤儿流水让钱包对账永远差数)
SELECT '3.9' AS check_id,
       '钱包流水指向已删钱包' AS issue,
       wt.id AS row_id,
       wt.wallet_id AS missing_parent_id
FROM wallet_transactions wt
LEFT JOIN meal_wallets mw ON mw.id = wt.wallet_id
WHERE mw.id IS NULL;

-- 3.10 dispatch_batch_items → dispatch_batches(批次项挂不存在的批次)
SELECT '3.10' AS check_id,
       '批次项指向已删批次' AS issue,
       dbi.id AS row_id,
       dbi.batch_id AS missing_parent_id
FROM dispatch_batch_items dbi
LEFT JOIN dispatch_batches db ON db.id = dbi.batch_id
WHERE db.id IS NULL;

-- 3.11 rider_address_bindings → customers / customer_addresses
-- (地址绑定是派单归区的记忆来源;父行被删后记忆命中即指向空,
--  归区结果错乱且不再自动刷新)
SELECT '3.11' AS check_id,
       '地址绑定指向已删客户/地址' AS issue,
       rab.id AS row_id,
       rab.customer_id,
       rab.address_id,
       CASE WHEN c.id IS NULL THEN 'customer' ELSE 'address' END AS missing_side
FROM rider_address_bindings rab
LEFT JOIN customers c ON c.id = rab.customer_id
LEFT JOIN customer_addresses ca ON ca.id = rab.address_id
WHERE c.id IS NULL OR ca.id IS NULL;

-- 3.12 餐次订单指向已删地址（9.2 事故：吴天豪 3 个进行中订单因地址被物理删除,
--      订单中心 INNER JOIN 地址表把它们整体藏掉, 订单中心 79 vs 看板 82 对不上）
SELECT '3.12' AS check_id,
       '餐次订单指向已删地址' AS issue,
       mso.id AS row_id,
       mso.address_id AS missing_parent_id,
       mso.status
FROM meal_slot_orders mso
LEFT JOIN customer_addresses ca ON ca.id = mso.address_id
WHERE mso.address_id IS NOT NULL
  AND ca.id IS NULL;

-- =============================================================================
-- 四、空值语义异常:字段非 NULL 但值在业务上不合法
-- =============================================================================

-- 4.1 rider_address_bindings 空壳记录:area_code = '' 且 rider_profile_id IS NULL
-- 为什么:区域按餐期迁移(V18/V23)与「先占位后回填」的写入路径产生过
--   既无区域也无骑手的空壳记忆行;派单归区读取记忆命中空壳时,订单会被
--   归到空区域,派单中心找不到承接骑手,订单永远停在待派。
SELECT '4.1' AS check_id,
       '空壳地址绑定记忆' AS issue,
       rab.id,
       rab.customer_id,
       rab.address_id,
       rab.meal_period,
       rab.area_code,
       rab.rider_profile_id,
       rab.updated_reason,
       rab.updated_at
FROM rider_address_bindings rab
WHERE rab.area_code = ''
  AND rab.rider_profile_id IS NULL;

-- 4.2 钱包超扣:consumed_meals > total_meals
-- 为什么:钱包扣减走条件 UPDATE「(total_meals - consumed_meals) >= ?」原子防超扣
--   (CustomerAssetServiceImpl.deductMeals),但人工调整/历史迁移若绕过条件
--   仍会写超;超扣钱包在顾客端显示负余额,套餐有效性判断失真。
--   与 admin E2E / CrossEndFlowE2ETest 的断言同口径。
SELECT '4.2' AS check_id,
       '钱包超扣' AS issue,
       w.id AS wallet_id,
       w.customer_id,
       w.total_meals,
       w.consumed_meals,
       w.reserved_meals,
       w.active
FROM meal_wallets w
WHERE w.consumed_meals > w.total_meals;

-- 4.3 订单份数非法:quantity <= 0
-- 为什么:份数参与钱包扣减与批次 total_count 汇总(V24 第5步口径);
--   0/负数会让批次统计为 0、钱包不扣款却照常出餐。
SELECT '4.3' AS check_id,
       '订单份数非法' AS issue,
       mso.id AS order_id,
       mso.status,
       mso.quantity,
       mso.meal_period
FROM meal_slot_orders mso
WHERE mso.quantity <= 0;

-- 4.4 订阅规则日期区间倒挂:end_date < start_date
-- 为什么:规则按 (start_date, end_date) 与当日比较决定是否生成订单;
--   倒挂区间永不命中,顾客以为在订,实际一单都不会生成,且无任何报错。
SELECT '4.4' AS check_id,
       '订阅规则日期倒挂' AS issue,
       sr.id AS rule_id,
       sr.customer_id,
       sr.active,
       sr.paused,
       sr.start_date,
       sr.end_date
FROM subscription_rules sr
WHERE sr.end_date < sr.start_date;

-- 4.5 客户地址空壳:地址文本或联系电话为空白
-- 为什么:骑手端按 address_line 文本导航/联系 contact_phone;
--   空白地址让导航无目标、电话拨不出,送达环节直接卡死。
SELECT '4.5' AS check_id,
       '客户地址空壳' AS issue,
       ca.id AS address_id,
       ca.customer_id,
       ca.contact_name,
       ca.contact_phone,
       LEFT(ca.address_line, 40) AS address_preview,
       ca.area_code
FROM customer_addresses ca
WHERE TRIM(ca.address_line) = ''
   OR TRIM(ca.contact_phone) = '';

-- =============================================================================
-- 巡检结束。附:历史事故→检查项对照速查
--   V20 钱包/回执重复        → 1.1 / 1.3
--   V21 订阅规则重复          → 1.2
--   V30 备注错列重复          → 1.4
--   V24 终态订单挂活跃派单    → 2.1 / 2.3 / 2.4
--   V28 取消退款残留派单      → 2.2
--   V27/V29 订单/客户硬删孤儿 → 3.1 ~ 3.11
--   V18/V23 空壳地址绑定      → 4.1
--   钱包原子扣减不变量        → 4.2
-- =============================================================================
