-- 固定订餐导入跳过记录
-- 后台操作员在"导入固定订餐"弹窗中取消勾选（跳过）某客户某餐次后记录于此。
-- 之后再次打开该日期该餐次的导入弹窗时，被跳过的客户不再出现在待导入列表中，
-- 操作员仍可在弹窗中"恢复"，恢复后即删除本记录。
CREATE TABLE `subscription_import_skips` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `serve_date` date NOT NULL,
  `customer_id` bigint NOT NULL,
  `meal_period` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LUNCH / DINNER',
  `skipped_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_import_skips_date_customer_meal` (`serve_date`, `customer_id`, `meal_period`),
  KEY `idx_subscription_import_skips_serve_date` (`serve_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
