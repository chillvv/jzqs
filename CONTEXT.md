# CONTEXT.md

jzqs（简知轻食）领域术语表。**纯词汇表，不是 spec，不是实现笔记，不是草稿纸。**
只包含：概念的名字、它是什么、以及容易混淆的邻界。实现细节一律不写在此文件。

## 核心概念

**Customer（客户）** — 订餐的顾客。`customers` 是按微信 openid 标识的自然人。
_不是_ `users`（后台/骑手的操作账号）。一个 Customer 不是 User。

**MealSlotOrder（餐段订单）** — 客户在某个餐段的订餐。`meal_slot_orders`。
_不是_ `daily_orders`（按天聚合的汇总视图）。说"订单"时默认指 MealSlotOrder。

**MealWallet（餐钱包）** — 客户的套餐余量。`meal_wallets`。每个客户在给定范围内**至多一个生效钱包**（唯一约束兜底）。
**WalletTransaction（钱包流水）** — 钱包的每一次增减，`wallet_transactions`。

**DispatchBatch（派单批次）** — 一次派单动作产生的批次，`dispatch_batches`。
**DispatchAssignment（派单分配）** — 批次里的一条具体指派（哪个骑手送哪个地址），`dispatch_assignments`。
_区分_：Batch 是"这一轮派单"，Assignment 是"这一轮里的一单"。

**RiderProfile（骑手档案）** — 骑手本体，`rider_profiles`。
**RiderAddressBinding（骑手地址绑定）** — 骑手与配送地址的绑定关系，`rider_address_bindings`。按餐段拆分（V23）。

**Address（地址）** — 客户的配送地址，`customer_addresses`。
**AreaCode（区域码）** — 地址的配送分区标识。

## 订阅

**SubscriptionRule（订阅规则）** — 客户的订阅设置，`subscription_rules`。每个客户至多一行。
**CustomerDeliverySubscription（配送订阅）** — `customer_delivery_subscriptions`。
**CustomerNightlySubscription（夜间订阅）** — `customer_nightly_subscriptions`。
**SubscriptionConfirmation（订阅确认）** — `subscription_confirmations`。
**SubscriptionImportSkip（订阅导入跳过项）** — `subscription_import_skips`。

## 备注

**OrderUserNote（用户备注）** — 客户侧对某 MealSlotOrder 的要求（少饭、不要辣等）。
**OrderMerchantRemark（商家备注）** — 运营侧对某 MealSlotOrder 的要求（重点关注、本餐送果蔬汁等）。
两侧**各自独立成栏**，互不覆盖。

按来源再分两类，**同栏内合并展示**（2026-08-29 老板拍板：长期 + 一次性都用逗号隔开一起出现）：
- **长期备注** — 挂在 Customer 上，`customer_notes`。对所有订单生效。
- **一次性备注** — 只挂在某一单上，落在 `meal_slot_orders` 的订单列（`user_note` / `merchant_remark`）。

**OrderNoteSnapshot（订单备注快照）** — `order_notes`，是上面两类来源的**投影**，不是第二套存储。
`note_type` 区分 USER / MERCHANT，`source_type` 区分来源（CUSTOMER_PROFILE / CUSTOMER_ORDER_INPUT /
MERCHANT_ORDER_ONCE / MERCHANT_PROFILE / MERCHANT_TIME_BOXED）。每次写备注都全删重写。
_纪律_：改了订单列的入口必须重建快照；不要往 `order_notes` 直接插行（会被重写冲掉）。

## 状态语言

**订单状态** 与 **派单状态** 是两套独立的状态机，必须同步（走状态机 helper）：
- MealSlotOrder 进入终态时，其 DispatchAssignment 不得仍在活跃态。
- 跨表状态不同步是历史事故根因（V24 修复）。

**终态 / 活跃态** — 讨论状态时用这两个词，不要枚举具体枚举值。

## 幂等与并发

**IdempotencyRecord（幂等记录）** — `idempotency_records`，写操作的幂等落库凭据。幂等必须落库，不用内存实现。

## 其他

**AftersaleCase（售后工单）** — `aftersale_cases`，及其动作 `aftersale_actions`。
**DeliveryException（配送异常）** — `delivery_exceptions`。
**DeliveryReceipt（配送回执）** — `delivery_receipts`。
**MenuWeek（周菜单）** — `menu_weeks` 与其条目 `menu_week_items`。
**PackagePlan（套餐）** — `package_plans`。
**CostEntry（成本条目）** — `cost_entries`。
**DispatchAISettings / DispatchAIJobLog** — AI 派单的配置与执行日志。
**DispatchRouteSuggestion（路线建议）** — AI 派单产出的路线建议及其条目/反馈。
**AdminOperationLog（后台操作日志）** — `admin_operation_logs`。
**MaintenanceJobLog（维护任务日志）** — `maintenance_job_logs`。

## 术语纪律

- 说"订单"指 **MealSlotOrder**；指 `daily_orders` 时必须说"日报单"。
- 说"派单"需区分 **Batch**（批次）还是 **Assignment**（分配）。
- 说"订阅"需指明是哪一张表（Rule / Delivery / Nightly），四者不是一回事。
- 说"备注"需区分 **用户备注** 还是 **商家备注**，以及 **长期** 还是 **一次性**。`order_notes` 只是投影。
- 新概念先在此登记，再进代码。命名冲突时以此文件为准。
