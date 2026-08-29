-- ============================================================================
-- 修正被错放进「用户备注」栏的商家备注
-- ============================================================================
-- 背景：writeOrderSnapshot 原先有一个 subscriptionDefaultNote 形参，落库成
--       note_type='USER' + source_type='SUBSCRIPTION_DEFAULT'。
--       唯一调用方 OrderOperationServiceImpl.manualCreateWithDate 把「后台录单
--       填的商家备注」塞给了它，导致商家备注显示在「用户备注」栏（还是红色告警色）。
--
--       该形参已移除，代码不再产生 SUBSCRIPTION_DEFAULT 行。本迁移把历史上已错
--       放的行改判到商家侧，避免老订单一直显示在错误的栏位。
--
-- 幂等：改判后再执行无匹配行；去重语句本身也是幂等的。
-- ============================================================================

UPDATE order_notes
SET note_type = 'MERCHANT',
    source_type = 'MERCHANT_ORDER_ONCE',
    scope_type = 'ORDER_ONCE'
WHERE source_type = 'SUBSCRIPTION_DEFAULT';

-- 改判后可能与同订单既有的商家备注重复（同 note_type + 同 source_type + 同内容），
-- 保留 id 最小的一条，其余删除，避免订单备注接口返回重复条目。
DELETE on1
  FROM order_notes on1
  JOIN order_notes on2
    ON on2.meal_slot_order_id = on1.meal_slot_order_id
   AND on2.note_type = on1.note_type
   AND on2.source_type = on1.source_type
   AND on2.content = on1.content
   AND on2.id < on1.id;
