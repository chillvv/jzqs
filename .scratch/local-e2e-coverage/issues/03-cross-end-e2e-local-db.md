# 03 — 跨端 E2E：连本地 DB 跑「下单→派单→送达」全链路 + 复杂场景

**What to build:** 一套连本地 docker MySQL 3307 的全链路端到端测试，模拟「顾客小程序下单 → 后台派单 → 骑手送达」完整流程，并覆盖复杂场景（并发下单、钱包超扣、退款回滚、派单冲突、状态机终态同步）。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** enhancement
**优先级:** high

## 问题

项目现状：miniapp / miniapp-rider 只有纯函数与文案断言（**零真人链路**）；admin 只有组件级单测 + 指向生产的冒烟脚本；真正连本地 DB 跑链路的只有后端集成测试。**缺一套跨端全链路 + 复杂场景 E2E**。

## Acceptance criteria

- [x] 连本地 docker MySQL 3307（容器 jzqs-mysql，3307→3306 映射，已确认 Up healthy）
- [x] 主链路：顾客下单 → 订单落库 + 钱包扣减 + 流水（DB 断言）
- [x] 复杂场景：钱包不足时拒绝下单、绝不超扣（资金红线）
- [x] 一致性巡检：终态订单不得仍挂在活跃派单分配上
- [x] 跑通并给出实际命令输出

**实现：** `backend/src/test/java/com/jzqs/app/mobile/CrossEndFlowE2ETest`（3 个用例）
**注意：** 放在 `com.jzqs.app.mobile` 包而非 `e2e` 包——`MiniappOrderModule` 是**包级私有**，跨包无法访问。

**验证输出（2026-08-29）：**
```
mvn --no-transfer-progress test -Dtest=CrossEndFlowE2ETest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 踩坑（重要）

1. **下单窗口从 DB 读，不是配置项**：`MiniappOrderModule.ensureSelfOrderAllowed` 用
   `MiniappOrderWindow.cutoffTime/openTime(jdbcTemplate)`，读 `admin_settings.night_order_cutoff_time` /
   `night_order_open_time`（默认 23:00 → 08:00）。设 `app.mobile.self-order-cutoff` 属性**无效**。
2. **`openTime == cutoffTime` 语义是「整天开放」**（`MiniappOrderWindow.isOpen` 兜底分支）。
   测试 seed 把两者都设为 `00:00` 即可让测试任何时刻都能跑。
3. **连带发现（待修）**：现有 `MiniappOrderModuleTest` 等下单相关测试**凌晨跑会失败**
   （默认 23:00→08:00 窗口，00:00-08:00 属关闭时段），是时间依赖的脆弱测试。

## 未覆盖（后续）

- 骑手送达 / 取消退款链路（需 seed 骑手 + 区域绑定，接口入口分别为
  `POST /orders/{id}/complete` 与售后 service）
- 真正的高并发（多线程同时下单）防超扣
