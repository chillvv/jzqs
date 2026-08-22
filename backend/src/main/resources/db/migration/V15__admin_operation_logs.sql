-- V15: 后台操作审计日志表
-- 记录所有 /api/admin/** 写操作(POST/PUT/DELETE/PATCH)的操作人、操作内容与结果
CREATE TABLE IF NOT EXISTS `admin_operation_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人用户ID(users.id)，登录尝试等无身份场景为NULL',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名(登录尝试时为尝试的手机号)',
    `operator_phone` VARCHAR(32) DEFAULT NULL COMMENT '操作人手机号',
    `operator_role` VARCHAR(32) DEFAULT NULL COMMENT '操作人角色(OWNER/ADMIN/OPERATOR)',
    `module` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务模块(取@AuditAction或路径推导，如CUSTOMER_ASSET)',
    `action` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务动作(取@AuditAction或路径推导，如WALLET_GRANT)',
    `http_method` VARCHAR(16) NOT NULL DEFAULT '' COMMENT 'HTTP方法(POST/PUT/DELETE/PATCH)',
    `request_path` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '请求路径',
    `request_summary` VARCHAR(1000) DEFAULT NULL COMMENT '请求参数摘要(敏感字段已脱敏，超长截断)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果状态(SUCCESS/FAILED)',
    `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败时的错误信息',
    `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
    `client_ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_op_logs_created_at` (`created_at`),
    KEY `idx_admin_op_logs_operator_id` (`operator_id`),
    KEY `idx_admin_op_logs_module` (`module`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '后台操作审计日志';
