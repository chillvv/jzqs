-- 将晚餐（DINNER）的骑手与配送区域各复制一份到午餐（LUNCH）。
-- 背景：V16 引入餐段维度时，历史骑手/区域统一归到 DINNER，午餐餐段无任何数据，
--       导致"骑手管理 / 区域管理"切换午餐时为空。本迁移一次性补齐午餐数据。
-- 说明：骑手端任务队列按 rider_name 关联（JOIN rider_profiles ... WHERE rider_name = ?），
--       同名骑手在 LUNCH/DINNER 各有一条记录不会造成任务丢失；微信绑定信息不复制，
--       避免同一微信号匹配歧义（登录绑定仍走手机号命中 DINNER 记录）。

-- 1) 复制骑手到午餐：保留手机号便于后台查看；wechat_open_id / current_openid 不复制
INSERT INTO rider_profiles (
    rider_name,
    phone,
    wechat_open_id,
    employment_status,
    default_area_code,
    display_order,
    remark,
    current_openid,
    auth_status,
    display_name,
    meal_period
)
SELECT
    src.rider_name,
    src.phone,
    NULL,
    src.employment_status,
    src.default_area_code,
    src.display_order,
    src.remark,
    NULL,
    src.auth_status,
    src.display_name,
    'LUNCH'
FROM rider_profiles src
WHERE src.meal_period = 'DINNER'
  AND NOT EXISTS (
      SELECT 1 FROM rider_profiles t
      WHERE t.rider_name = src.rider_name AND t.meal_period = 'LUNCH'
  );

-- 2) 复制区域绑定到午餐，并把默认/备用骑手指向对应的午餐骑手
INSERT INTO dispatch_area_bindings (
    area_code,
    default_rider_profile_id,
    backup_rider_profile_id,
    updated_by,
    updated_at,
    keywords,
    meal_period
)
SELECT
    src.area_code,
    lrp.id,
    lbrp.id,
    src.updated_by,
    src.updated_at,
    src.keywords,
    'LUNCH'
FROM dispatch_area_bindings src
LEFT JOIN rider_profiles drp ON drp.id = src.default_rider_profile_id
LEFT JOIN rider_profiles lrp ON lrp.rider_name = drp.rider_name AND lrp.meal_period = 'LUNCH'
LEFT JOIN rider_profiles dbrp ON dbrp.id = src.backup_rider_profile_id
LEFT JOIN rider_profiles lbrp ON lbrp.rider_name = dbrp.rider_name AND lbrp.meal_period = 'LUNCH'
WHERE src.meal_period = 'DINNER'
  AND NOT EXISTS (
      SELECT 1 FROM dispatch_area_bindings t
      WHERE t.area_code = src.area_code AND t.meal_period = 'LUNCH'
  );
