-- 每晚提醒订阅授权表（按用户维度）
-- 用户在小程序端勾选"总是保持"授权优惠券过期提醒模板后，后台即可每晚定时群发。
CREATE TABLE `customer_nightly_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `template_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'AUTHORIZED / FAILED / REJECTED',
  `source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MINIAPP',
  `authorized_at` datetime NOT NULL,
  `last_sent_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_nightly_subscriptions_customer` (`customer_id`),
  KEY `idx_customer_nightly_subscriptions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
