# 03 — 订单详情备注醒目展示

**What to build:** 订单详情页有任何备注（用户备注 / 商家嘱咐）时，以独立、醒目的「订单备注」卡片展示，
而不是埋在配送地址卡片里的不显眼小块。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** high

## Acceptance criteria

- [x] 用户备注与商家嘱咐以独立卡片展示，位于配送地址卡片之后（骑手进详情第二眼就能看到）
- [x] 有备注时卡片带警示底色与图标，与普通信息卡明显区分
- [x] 空备注（`-` / 空串）不显示卡片，也不显示空行
- [x] 长备注换行不溢出（word-break）
- [x] `order-detail-experience.test.js` 更新断言覆盖新卡片结构
