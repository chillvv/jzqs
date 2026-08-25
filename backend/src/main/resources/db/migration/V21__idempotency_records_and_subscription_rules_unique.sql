-- 数据一致性加固（续 V20）：幂等流水落库 + subscription_rules 每客户唯一。
-- 1) @Idempotent 原为纯内存存储（重启即失效、多实例不共享、无流水可查），
--    新增 idempotency_records 表，以 key_hash 唯一约束作为防重复提交的最终防线。
-- 2) subscription_rules 所有读取路径（移动端 selectOne(customer_id)、各报表 ORDER BY id DESC /
--    MAX(id) 子查询）均假设每客户至多一条规则，一客户多行会令移动端读写直接抛
--    TooManyResults 异常，故补 UNIQUE(customer_id) 兜底，并先按"保留最新一条"清理历史重复。

CREATE TABLE `idempotency_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key_hash` char(64) NOT NULL COMMENT '幂等键 SHA-256 十六进制（actionKey|身份|路径|请求体摘要）',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING-执行中 / SUCCEEDED-已成功',
  `expires_at` timestamp(3) NOT NULL COMMENT '占用截止时间，到期后可被新请求接管并由定时任务清理',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_records_key_hash` (`key_hash`),
  KEY `idx_idempotency_records_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口幂等占用流水表';

-- 清理 subscription_rules 历史重复：每客户保留最新一条（读取方均按 id 倒序取最新，语义不变）。
DELETE sr
FROM subscription_rules sr
JOIN (
    SELECT customer_id, MAX(id) AS keep_id
    FROM subscription_rules
    GROUP BY customer_id
    HAVING COUNT(*) > 1
) dup ON dup.customer_id = sr.customer_id AND sr.id <> dup.keep_id;

ALTER TABLE `subscription_rules`
  ADD UNIQUE KEY `uk_subscription_rules_customer` (`customer_id`);
