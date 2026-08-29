# 订单备注：用户/商家两侧各自合并展示（长期 + 一次性，逗号分隔）

## 背景（Bug）

订单备注在库上有两套存储，读取规则却是"有快照就整列丢弃"：

- **列**：`meal_slot_orders.note` / `user_note`（用户侧）、`meal_slot_orders.merchant_remark`（商家侧）
- **快照表**：`order_notes`（`note_type` = USER / MERCHANT，`source_type` = CUSTOMER_PROFILE /
  CUSTOMER_ORDER_INPUT / SUBSCRIPTION_DEFAULT / MERCHANT_ORDER_ONCE / MERCHANT_PROFILE / MERCHANT_TIME_BOXED）

三处读取口（`OrderQueryRepository` / `RiderQueueSupport` / `DispatchQueryModule`）都是：

```java
if (projection != null && projection.hasOrderNotes()) {
    return 投影;      // 只用 order_notes
}
return 列值;          // 才回退到列
```

而 `OrderNoteSnapshotServiceImpl.writeOrderSnapshot` 第一行就 `deleteSnapshots(orderId)` 全删重建，
重建时 `orderOnceMerchantNotes` 三个调用方全部传 `List.of()`（恒空）——**订单级商家备注从未进过快照**。

后果：后台录单/编辑的商家备注只落在列里，一旦用户在小程序下单或加餐触发快照重建，
`hasOrderNotes()` 变 true，列值被永久忽略 → **用户备注一出现，商家备注就消失**。

次生问题：
1. `OrderOperationServiceImpl.manualCreateWithDate` 把商家备注塞给了 `subscriptionDefaultNote` 形参，
   落库成 `note_type='USER' source_type='SUBSCRIPTION_DEFAULT'` → **商家备注显示在用户备注栏**。
2. 快照重建全删，会带走后台 `addOrderNote` 写的 `MERCHANT_ORDER_ONCE` 行。

## 决策（老板拍板，2026-08-29）

> 无论是用户还是商家的长期备注还是一次性快照，都应该用逗号隔开一起出现。

## 规格

### S1 展示规则

- **用户备注** = 该订单所有 `note_type='USER'` 的 ACTIVE 快照行 ∪ `meal_slot_orders.user_note`/`note` 列值
- **商家备注** = 该订单所有 `note_type='MERCHANT'` 的 ACTIVE 快照行 ∪ `meal_slot_orders.merchant_remark` 列值
- 两侧**各自独立成栏**，栏内按顺序**去重后用中文逗号「，」拼接**
- 列值只作为兜底来源追加在快照之后（不覆盖、不替换）
- 空值与 `-` 视为无备注，不参与拼接

### S2 快照规则

- 快照重建时**保留**后台经由 `addOrderNote` 写入的 `MERCHANT_ORDER_ONCE` 行
- 重建后该行与调用方传入的 `orderOnceMerchantNotes` 合并去重

### S3 写入规则

- 后台录单的商家备注走 `orderOnceMerchantNotes`（不再是 `subscriptionDefaultNote`），
  保证它落在 MERCHANT 侧而不是 USER 侧
- 订单备注合并（`mergeOrderNote`）的分隔符统一为「，」，与展示分隔符一致

## 业务不变量（每 X 至多 Y）

| 不变量 | 约束方式 |
|---|---|
| 每订单的用户备注栏内不出现重复条目 | 应用层去重（本次实现）；`order_notes` 无唯一约束（同一内容可来自不同 source） |
| 每订单的商家备注栏内不出现重复条目 | 应用层去重（同上） |
| 每订单的 `order_notes` 行归属唯一订单 | 已有 `fk_order_notes_order`（V25） |

> 备注是自由文本、允许多条同内容来自不同来源，**不宜**加 UNIQUE，去重放在投影层。

## 影响面

- 后端：`OrderQueryRepository` / `RiderQueueSupport` / `DispatchQueryModule` / `OrderNoteSnapshotServiceImpl` /
  `OrderNoteSnapshotRepository` / `OrderOperationServiceImpl` / `AbstractOrderPrepSupport` / `MiniappOrderModule`
- 前端：无需改动（后端返回已拼好的字符串）；`buildOrderRemarkLine` 的「用户备注：X；商家备注：Y」
  段间分隔符保持「；」不变
- 数据库：无迁移（列值兜底保证历史数据不丢）

## 验收标准

- [x] 订单同时有「客户档案长期备注」+「本单一次性备注」时，两栏各自逗号拼接且都出现
- [x] 后台录单填商家备注 → 用户下单触发快照重建 → 商家备注**仍在商家栏**
- [x] 后台录单填商家备注 → 不出现在用户备注栏
- [x] 骑手端 / 派单中心 / 后台订单中心三处展示一致
- [x] 后台 `addOrderNote` 加的一次性商家备注，在用户加餐触发快照重建后仍在
- [x] `mvn -B clean test` → **280 tests / 0 failures / 0 errors / BUILD SUCCESS**

## 实现补记（与原始方案的差异）

1. **投影改并集时新增了 `orderMerchantRemark` 字段**：`merchantRemark` 现在是合并后的展示串，
   若后台编辑弹窗还拿它回写订单列，会把客户长期备注一起存进订单并再次重复展示。
   故 `OrderPrepItemResponse` 增加「只属于本单」的原始列值供编辑使用，前端编辑框改读它。
2. **`addOrderNote` 由「另插 order_notes」改为「合并进订单列 + 重建快照」**，消掉第二套实现（铁律 5），
   `insertOrderNote` 已删除。
3. 顺手修了 `OrderNoteSnapshotServiceTest` 的测试隔离脆弱性：它用 `CUSTOMER_ID = 1`，
   而 V1 基线客户从 382 起，单跑该类的 INSERT 会撞 `fk_customer_addresses_customer`。
   已加 `INSERT IGNORE` 兜底，不再依赖别的测试类先建客户。
