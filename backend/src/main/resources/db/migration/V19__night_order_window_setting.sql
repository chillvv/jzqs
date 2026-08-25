-- 系统设置增加「小程序下单窗口」：每晚截止时间 与 明早开放时间。
-- 该窗口用于小程序自助下单时段控制：每晚截止时间(默认 23:00) 之后至 明早开放时间(默认 08:00) 之前不接受新订单。
ALTER TABLE `admin_settings`
  ADD COLUMN `night_order_cutoff_time` varchar(5) NOT NULL DEFAULT '23:00' COMMENT '小程序每晚截止下单时间(HH:mm)',
  ADD COLUMN `night_order_open_time` varchar(5) NOT NULL DEFAULT '08:00' COMMENT '小程序明早开放下单时间(HH:mm)';
