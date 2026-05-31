CREATE TABLE admin_settings (
    id BIGINT PRIMARY KEY,
    ordering_enabled BOOLEAN NOT NULL,
    holiday_notice_title VARCHAR(128) NOT NULL,
    holiday_notice_desc VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO admin_settings (id, ordering_enabled, holiday_notice_title, holiday_notice_desc, updated_at) VALUES
    (1, TRUE, '节假日/店休特殊公告', '在小程序首页顶部展示的提示信息', TIMESTAMP '2026-05-10 08:30:00');
