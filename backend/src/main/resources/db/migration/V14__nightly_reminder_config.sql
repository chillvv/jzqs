-- 每晚用餐提醒：可调发送时间与可编辑文案
ALTER TABLE admin_settings
    ADD COLUMN nightly_reminder_time varchar(8) NOT NULL DEFAULT '20:00' COMMENT '每晚提醒发送时间(HH:mm)',
    ADD COLUMN nightly_reminder_description varchar(20) NOT NULL DEFAULT '再忙也要好好吃饭哟🍽' COMMENT '每晚提醒模板 thing3(描述), 限20字',
    ADD COLUMN nightly_reminder_tip varchar(20) NOT NULL DEFAULT '需要明日餐食的宝子现在可以下单喽～' COMMENT '每晚提醒模板 thing6(温馨提示), 限20字';
