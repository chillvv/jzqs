DELETE FROM menu_week_items WHERE week_id IN (1, 2, 3);
DELETE FROM menu_weeks WHERE id IN (1, 2, 3);

INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by)
VALUES
    (1, DATE '2026-05-11', DATE '2026-05-17', 'PUBLISHED', TIMESTAMP '2026-05-10 09:00:00', 'system', 'system'),
    (2, DATE '2026-05-18', DATE '2026-05-24', 'DRAFT', NULL, 'system', NULL),
    (3, DATE '2026-05-25', DATE '2026-05-31', 'DRAFT', NULL, 'system', NULL);

INSERT INTO menu_week_items (week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order)
VALUES
    (1, DATE '2026-05-11', 1, 'LUNCH', 'ACTIVE', '["椒麻鸡丁","火腿洋葱滑蛋","清甜焖白菜","玉米渣饭"]', 561, '椒麻鸡丁+火腿洋葱滑蛋+清甜焖白菜+玉米渣饭', '椒麻鸡丁+火腿洋葱滑蛋+清甜焖白菜+玉米渣饭', 561, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-11', 1, 'DINNER', 'ACTIVE', '["双椒鱼片","豉汁腐竹蒸肉片","时令鲜苋菜","玉米渣饭"]', 532, '双椒鱼片+豉汁腐竹蒸肉片+时令鲜苋菜+玉米渣饭', '双椒鱼片+豉汁腐竹蒸肉片+时令鲜苋菜+玉米渣饭', 532, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-12', 2, 'LUNCH', 'ACTIVE', '["香辣酱鸭翅","罗勒牛肉豆腐","蒜香青茄片","玉米渣饭"]', 593, '香辣酱鸭翅+罗勒牛肉豆腐+蒜香青茄片+玉米渣饭', '香辣酱鸭翅+罗勒牛肉豆腐+蒜香青茄片+玉米渣饭', 593, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-12', 2, 'DINNER', 'ACTIVE', '["跷脚牛肉（中份）","干锅培根杏鲍菇","玉米渣饭"]', 496, '跷脚牛肉（中份）+干锅培根杏鲍菇+玉米渣饭', '跷脚牛肉（中份）+干锅培根杏鲍菇+玉米渣饭', 496, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-13', 3, 'LUNCH', 'ACTIVE', '["避风塘大虾","西葫芦香炒蛋","枪炒上海青","白米饭"]', 528, '避风塘大虾+西葫芦香炒蛋+枪炒上海青+白米饭', '避风塘大虾+西葫芦香炒蛋+枪炒上海青+白米饭', 528, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-13', 3, 'DINNER', 'ACTIVE', '["泰式薄荷脆皮鸡排饭"]', 355, '泰式薄荷脆皮鸡排饭', '泰式薄荷脆皮鸡排饭', 355, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-14', 4, 'LUNCH', 'ACTIVE', '["哈汁牛肉拌饭"]', 412, '哈汁牛肉拌饭', '哈汁牛肉拌饭', 412, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-14', 4, 'DINNER', 'ACTIVE', '["奥尔良翅根","菜心香焖肉","捞汁时蔬","白米饭"]', 547, '奥尔良翅根+菜心香焖肉+捞汁时蔬+白米饭', '奥尔良翅根+菜心香焖肉+捞汁时蔬+白米饭', 547, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-15', 5, 'LUNCH', 'ACTIVE', '["十香醉排骨","香干溜肉丝","红烧鲜冬瓜","藜麦饭"]', 601, '十香醉排骨+香干溜肉丝+红烧鲜冬瓜+藜麦饭', '十香醉排骨+香干溜肉丝+红烧鲜冬瓜+藜麦饭', 601, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-15', 5, 'DINNER', 'ACTIVE', '["广东白切鸡","番茄烘金蛋","酸辣土豆丝","藜麦饭"]', 558, '广东白切鸡+番茄烘金蛋+酸辣土豆丝+藜麦饭', '广东白切鸡+番茄烘金蛋+酸辣土豆丝+藜麦饭', 558, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-16', 6, 'LUNCH', 'ACTIVE', '["水煮肉片（中份）","香脂松花椰","藜麦饭"]', 480, '水煮肉片（中份）+香脂松花椰+藜麦饭', '水煮肉片（中份）+香脂松花椰+藜麦饭', 480, '', '/assets/meal-default.jpeg', 1),
    (1, DATE '2026-05-16', 6, 'DINNER', 'ACTIVE', '["辣咖喱沙茶鸡腿排意面"]', 437, '辣咖喱沙茶鸡腿排意面', '辣咖喱沙茶鸡腿排意面', 437, '', '/assets/meal-default.jpeg', 2),
    (1, DATE '2026-05-17', 7, 'LUNCH', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (1, DATE '2026-05-17', 7, 'DINNER', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),

    (2, DATE '2026-05-18', 1, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-18', 1, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-19', 2, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-19', 2, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-20', 3, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-20', 3, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-21', 4, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-21', 4, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-22', 5, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-22', 5, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-23', 6, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-23', 6, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (2, DATE '2026-05-24', 7, 'LUNCH', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (2, DATE '2026-05-24', 7, 'DINNER', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),

    (3, DATE '2026-05-25', 1, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-25', 1, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-26', 2, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-26', 2, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-27', 3, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-27', 3, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-28', 4, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-28', 4, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-29', 5, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-29', 5, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-30', 6, 'LUNCH', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-30', 6, 'DINNER', 'UNCONFIGURED', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (3, DATE '2026-05-31', 7, 'LUNCH', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (3, DATE '2026-05-31', 7, 'DINNER', 'REST', '[]', NULL, NULL, NULL, NULL, NULL, NULL, 2);
