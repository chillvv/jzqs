# 02 — 物化链路事务化并清理孤儿空批次

**What to build:** 骑手队列物化（`ensureQueueBatch` 建批次头 → `ensureQueueBatchItem` 写明细）整体放进一个事务，
不再出现“批次头落库、明细回滚”的孤儿批次；并用一条 Flyway 迁移清理已存在的孤儿空批次。

**Blocked by:** None

**Status:** todo

**类型:** bug
**优先级:** medium

## 背景与证据

2026-09-10 实测：

```
批次总数 = 199，批次项总数 = 1295，其中没有明细的批次 = 44（22%）
```

样例（骑手 989）：

```
batch_id=1211  serve_date=2026-09-09  meal_period=DINNER  batch_status=IN_PROGRESS  real_items=0
batch_id=1209  serve_date=2026-09-10  meal_period=LUNCH   batch_status=READY        real_items=0
```

根因：`RiderQueueSupport` 整个类没有任何 `@Transactional`，
`ensureQueueBatch` 的 `INSERT INTO dispatch_batches` 与后续明细写入是彼此独立的自动提交语句，
明细语句一旦死锁/失败回滚，批次头就留了下来。这类批次头是死锁事故的“化石”。

## Acceptance criteria

- [ ] 物化路径（批次头 + 明细 + 状态刷新）在同一事务内提交，失败整体回滚
- [ ] 新增 Flyway 迁移（只增不删，版本号顺延）删除无明细的历史空批次；迁移需可重复执行且带条件
- [ ] 迁移执行后 `SELECT COUNT(*) FROM dispatch_batches db LEFT JOIN dispatch_batch_items dbi ON dbi.batch_id=db.id WHERE dbi.id IS NULL` 为 0
- [ ] 有集成测试覆盖“明细写入失败时批次头不残留”
- [ ] 注意：事务边界变长会加长持锁时间，需确认与 `01-read-path-write-lock` 的定点更新策略不冲突

## 备注

V25 迁移已有“孤儿清理”先例（见 `BaseDbIntegrationTest` 注释），本次沿用同一处理方式。
