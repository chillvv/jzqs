-- 系统设置增加「每晚提醒」总开关
ALTER TABLE `admin_settings`
  ADD COLUMN `nightly_reminder_enabled` tinyint(1) NOT NULL DEFAULT '1'
  AFTER `delivery_subscribe_dinner_time`;
