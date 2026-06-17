CREATE TABLE maintenance_cleanup_settings (
    module_key VARCHAR(64) PRIMARY KEY,
    module_label VARCHAR(64) NOT NULL,
    retention_value INT NOT NULL,
    retention_unit VARCHAR(16) NOT NULL DEFAULT 'DAY',
    auto_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO maintenance_cleanup_settings (module_key, module_label, retention_value, retention_unit, auto_enabled, sort_order)
VALUES
    ('ORDER_HISTORY', '订单历史', 30, 'DAY', TRUE, 10),
    ('DISPATCH_BATCH', '配送批次', 14, 'DAY', TRUE, 20),
    ('RECEIPT_RECORD', '回执记录', 1, 'DAY', TRUE, 30),
    ('NOTIFICATION_LOG', '通知日志', 14, 'DAY', TRUE, 40),
    ('DISPATCH_REASSIGNMENT', '区域调整记录', 14, 'DAY', TRUE, 50),
    ('ADDRESS_BINDING', '地址绑定', 90, 'DAY', TRUE, 60),
    ('WALLET_TRANSACTION', '钱包流水', 90, 'DAY', TRUE, 70);
