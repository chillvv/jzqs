# 01 — 消除骑手队列刷新的批次范围锁与写放大

**What to build:** 骑手刷新队列（列表/详情）触发的物化逻辑不再执行 `WHERE batch_id = ?` 的批次范围更新，
改为内存比对后按主键定点更新；同一批次一次刷新只刷一次；物化路径遇到死锁时退避重试，
不再把 `CannotAcquireLockException` 直接抛成 500 给骑手。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** critical

## Acceptance criteria

- [x] `refreshRiderBatchState` 不再出现 `WHERE batch_id = ?` 形式的 UPDATE，改为只对状态变化的行按 `id` 定点更新
- [x] 期望状态语义与旧实现逐行等价：`CURRENT` 降为 `PENDING` → 序号最小的 `PENDING` 升为 `CURRENT`，
      `DELIVERED` / `DEFERRED` 保持不变
- [x] 批头 `dispatch_batches`（total/delivered/current_sequence/batch_status）仅在数值变化时写
- [x] `syncDispatchAssignmentsFromBatch` 仅在 `sequence_number` / `status` 变化时写
- [x] `ensureRiderQueueMaterialized` 对涉及的批次去重后各刷一次（不再每订单刷一遍）
- [x] 物化路径对 `ConcurrencyFailureException` 退避重试最多 2 次
- [x] 新增回归测试 `RiderQueueConcurrentRefreshTest`（3 用例）：锁作用域、稳态零写入、并发一致性
- [x] 相关既有测试全绿（见下方验证记录）

## 验证记录（2026-09-10）

在旧代码上运行新测试（证明测试确实覆盖根因）：

```
refreshShouldNotLockOtherOrdersInSameBatch:
  expected: <OK> but was: <BLOCKED: Lock wait timeout exceeded; try restarting transaction>
steadyStateRefreshShouldNotWriteAnyRow:
  expected: <0> but was: <120>      // 5 次刷新产生 120 次 UPDATE
```

修复后：

```
RiderQueueConcurrentRefreshTest            Tests run: 3, Failures: 0, Errors: 0
RiderOrderSequenceModuleTest               Tests run: 1, Failures: 0, Errors: 0
RiderOrderStatusRevertModuleTest           Tests run: 1, Failures: 0, Errors: 0
RiderDeliveryEvidenceModuleTest            Tests run: 2, Failures: 0, Errors: 0
CrossEndFlowE2ETest（骑手全链路）           Tests run: 3, Failures: 0, Errors: 0
```

运行方式（测试库 3307 的口令与 .env 里的 `MYSQL_ROOT_PASSWORD` 一致，必须在命令行显式传入；
`TEST_DB_PASSWORD` 是给 `BaseDbIntegrationTest` 那批集成测试用的，不传会有 8 个 Error）：

```
cd backend && SPRING_DATASOURCE_PASSWORD=<MYSQL_ROOT_PASSWORD> TEST_DB_PASSWORD=<MYSQL_ROOT_PASSWORD> mvn -B test
```

## 上线记录（2026-09-10 13:10）

放行门槛：全量后端测试 **334 个用例全绿**（`Tests run: 334, Failures: 0, Errors: 0`，BUILD SUCCESS）。

```
./build.sh backend
→ Started JzqsApplication in 8.663 seconds
→ jzqs-backend  Up (healthy)
```

确认线上跑的是新代码（Dockerfile 是多阶段构建、从 `src` 重新编译，所以 jar 整体 md5 与宿主产物不同属正常）：
比对容器内 `BOOT-INF/classes/com/jzqs/app/mobile/RiderQueueSupport.class` 与宿主构建产物，
**md5 完全一致**（`b59f81ad2966251d6e4de1733fd44c04`），且包含新增的 `runWithLockRetry` /
`BatchHeaderSnapshot` / `BatchAssignmentSyncRow`。

部署后观测（13:15–13:20）：

```
Innodb_row_lock_waits: 2168972 → 2168972     // 120 秒零增长（修复前约 8–12 次/秒）
新容器日志 "Deadlock found": 0
```

> 注：该窗口骑手流量较低（http 处理日志 12 条），晚高峰（16:30–18:30）需要再复核一次。

