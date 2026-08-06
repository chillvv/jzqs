-- 流水记录增加"该笔调整后的餐包到期时间"快照列
-- 用于在流水列表中展示每次加餐/开卡对应的到期时间
ALTER TABLE `wallet_transactions`
    ADD COLUMN `expired_at_snapshot` timestamp NULL DEFAULT NULL COMMENT '该笔调整后的餐包到期时间' AFTER `snapshot_balance`;
