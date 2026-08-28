# 01 — 创建上线前一致性巡检 SQL 脚本

**What to build:** 一个可在生产库直接执行的 `scripts/consistency-check.sql`，覆盖四类脏数据检查（重复记录 / 跨表状态不一致 / 悬空外键 / 空值语义异常），每条查询带注释说明"为什么查这个"。发版前必跑。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**类型:** enhancement
**优先级:** medium

## 背景

参考 jzqs V20-V24 迁移教训：餐钱包重复、订阅规则重复、订单与派单状态不一致、空壳地址记录。这四类脏数据都曾在生产环境真实出现过。

## Acceptance criteria

- [ ] `scripts/consistency-check.sql` 可在生产库执行
- [ ] 1. 重复记录：`meal_wallets` 每客户至多一个生效钱包
- [ ] 2. 跨表状态：`meal_slot_orders` 终态 vs `dispatch_assignments` 活跃态
- [ ] 3. 空壳记录：`rider_address_bindings` area_code='' AND rider_profile_id IS NULL
- [ ] 4. 订阅规则重复：`subscription_rules` 每客户至多一行
- [ ] 每条查询有注释说明"为什么查这个"（对应哪次事故/哪条业务不变量）
