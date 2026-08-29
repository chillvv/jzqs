# 02 — 订单级商家备注收敛成单一落点，快照由它重建

**What to build:** 后台给订单加的「仅此单生效」商家备注，在用户加餐触发备注快照重建后必须还在；
后台代客录单填的商家备注必须显示在「商家备注」栏，不能跑到「用户备注」栏去。

**Blocked by:** 01（投影改造）

**Status:** done

**类型:** bug
**优先级:** high

## 实现要点（与原始设想的差异）

原始设想是「快照重建时保留库上既有的 `MERCHANT_ORDER_ONCE` 行」。实现时改成了更彻底的做法：

- **订单级商家备注的唯一落点是 `meal_slot_orders.merchant_remark` 列**，`order_notes` 只是投影。
- `writeOrderSnapshot` 删掉了 `subscriptionDefaultNote` 形参（它是把商家备注错放进用户侧的元凶），
  改为让调用方通过 `orderOnceMerchantNotes` 传入当前订单级商家备注。
- 所有改了这个列的入口（`updateMerchantRemark` / `updateOrderProfile` / `manualCreate` / 小程序下单与合并）
  都重建快照，所以「保留」是自然结果，不需要额外的保留逻辑。
- `addOrderNote` 由「另插一条 order_notes」（第二套实现）改为「合并进订单列 + 重建快照」，
  消掉双写（铁律 5）；`insertOrderNote` 已删除。

## Acceptance criteria

- [x] `OrderOperationRepository` 新增 `findOrderUserNote`，删除 `insertOrderNote`
- [x] `OrderOperationServiceImpl` 新增 `refreshOrderNoteSnapshot`，
      `updateMerchantRemark` / `updateOrderProfile` / `manualCreate` / `addOrderNote` 都走它
- [x] `manualCreate` 的商家备注走 `orderOnceMerchantNotes`，不再塞 `subscriptionDefaultNote`
- [x] `MiniappOrderModule` 下单/合并都把订单列上的商家备注传进快照（新增 `existingMerchantRemark`）
- [x] `AbstractOrderPrepSupport.mergeOrderNote` 与 `MiniappOrderModule.mergeOrderNote`
      的合并分隔符由「；」改为「，」
- [x] 新增回归测试：`addOrderNote` 落到订单列、不进用户侧、多次追加用逗号
- [x] 新增回归测试：录单的商家备注在快照里是 `MERCHANT` 侧而不是 `USER` 侧
- [x] 迁移 V30 把历史错放的 `SUBSCRIPTION_DEFAULT` 行改判到商家侧并去重
