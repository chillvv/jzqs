# 骑手队列刷新死锁（读路径写库 + 批次范围锁）

## 现象

用餐高峰，骑手端「刷新半天刷不出来数据」，偶发登录/刷新提示「无法连接服务器，请检查网络或联系管理员」，
过一会儿自行恢复。

## 根因（已定位）

`RiderQueueSupport` 的**读**路径 `ensureRiderQueueMaterialized` 每次刷新都写库，其中
`refreshRiderBatchState` 执行：

```sql
UPDATE dispatch_batch_items SET item_status = 'PENDING'
WHERE batch_id = ? AND item_status = 'CURRENT'
```

过滤条件只用 `batch_id`，而索引是 `uk_dispatch_batch_items_batch_sequence (batch_id, current_sequence)`，
`batch_id` 只是**非唯一前缀**。InnoDB 在 REPEATABLE READ 下必须扫描整批索引区间，并对扫到的每条记录
加 X 记录锁 + 记录间间隙锁。于是：

- 两个并发请求即使改的是**不同订单**，锁的是**同一段索引区间**；
- 同一请求内还存在加锁顺序反转（先 `UPDATE ... WHERE id=?` 锁单行，再 `refreshRiderBatchState` 锁整批）；
- 两者叠加直接成环 → 死锁 → 事务回滚 → 骑手侧 500。

失败的请求前端重试 2 次（`request.js`）后仍可能全失败，骑手表现为「刷不出来」；并发过去后恢复。

## 证据

- 后端日志 48h 内 21 次 `CannotAcquireLockException: Deadlock found`，堆栈固定指向
  `RiderQueueSupport.refreshRiderBatchState:884`，入口是 `RiderController.getOrderDetail:150`
- 死锁时间分布集中在用餐高峰（09-09 10:34–12:02 共 14 次，16:49–18:23 共 5 次）
- MySQL 最近一次死锁（2026-09-10 11:48:58）：两个事务执行同一句 UPDATE（`batch_id=1197`），
  一方持有 **22 个 X 记录锁**，全在 `uk_dispatch_batch_items_batch_sequence` 上
- `Innodb_row_lock_waits` 累计 1,252,988；锁等待总时长 ≈ 5.3 小时；MySQL 容器 BLOCK I/O 写入
  48.7GB，而业务表总计仅几 MB —— 典型的读路径写放大
- 骑手 989（截图手机号 18171321818）16:46 被派 13 单 → 16:49:08 死锁报错 → 16:49:25 重新登录成功，
  与「过一会自己就好了」一致
- 副作用：199 个批次中有 **44 个没有明细的空批次**（物化链路无事务：批次头先落库，后续明细语句回滚）

## 修复方案

1. `refreshRiderBatchState` 改为「一次性读批次明细 → 内存计算期望状态 → 只对状态真正变化的行按**主键**更新」，
   彻底去掉 `WHERE batch_id = ?` 的范围更新（根除 next-key/间隙锁）
2. 同一批次一次刷新只刷一次（旧实现按订单循环调用，15 单的批次单次刷新刷 15 遍）
3. 批头 `dispatch_batches` 与 `dispatch_assignments` 同步改为「先读后比，仅变化时写」，稳态零写入
4. 物化路径增加死锁退避重试，残余争抢不再把 500 抛给骑手

## 不在本次范围（另行开单）

- 44 个空批次的数据清理 + 物化链路整体事务化
- 骑手端 8s 轮询与实时事件的刷新去重
- 网关访问日志缺失（`Caddyfile` 无 `log` 指令），无法观测请求延迟与错误率
