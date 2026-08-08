-- 新增「休息提示模板」全局设置：商家在后台编辑一次后，周菜单设为休息时自动套用，
-- 并作为用户端未单独填写休息文案时的兜底展示文案。
ALTER TABLE admin_settings
  ADD COLUMN rest_notice_template varchar(255) NOT NULL DEFAULT '今日休息，不提供餐食';
