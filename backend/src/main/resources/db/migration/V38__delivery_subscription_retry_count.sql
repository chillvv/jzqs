-- 取餐提醒订阅消息发送重试次数。
-- 背景：此前发送失败（含用户拒收 43101）的记录每分钟都会被重新扫描并重试，
-- 永不终止，导致微信接口被反复空刷、后台线程池被拖慢，进而出现「到点消息成批延迟补发」。
-- 新增该列后，失败记录只重试有限次数，超过即放弃。
ALTER TABLE `customer_delivery_subscriptions`
  ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '订阅消息发送失败重试次数' AFTER `last_error_message`;
