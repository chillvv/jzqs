# 01 — 修复测试编译错误（25 个测试文件过期）

**What to build:** 让 `mvn test` 全量通过，终结"上线用 `-DskipTests` 跳过测试导致测试文件长期腐烂"的状态。

**Blocked by:** None — can start immediately

**Status:** resolved

**类型:** bug
**优先级:** high

## 问题

上线时用 `-DskipTests` 跳过测试，导致测试文件长期未同步方法签名变更。25 个测试文件编译失败，覆盖：幂等存储类替换、prepStats 加参数、grantPackage 加 operatorId、riderName→riderId、构造器字段变更等。

## Answer（2026-08-28 全量通过）

- [x] `build.sh` 采用远程版本（`RUN_BACKEND_TESTS=1` + `test` 子命令 + `init-test-db.sh`）
- [x] 修复全部测试编译错误
- [x] 新建 `TestIdempotencyStore`（内存幂等，修复 `@MockBean` 导致 409）
- [x] 19 个 `@WebMvcTest` 加 `@MockBean JdbcTemplate`（修复 `AdminOperationLogFilter` context 失败）
- [x] 修复 `AdminUserControllerTest` 401（缺 adminRole）
- [x] `OrderPrepControllerTest` 删除过时 direct-refund 测试
- [x] 修 V25 迁移孤儿清理前置（否则外键加不上）
- [x] 修 `CustomerMainSheetSyncServiceImpl` 生产 SQL 列数 bug（INSERT meal_wallets 8列9值）
- [x] 测试库连接改用 3307 端口（3306 被本地 MySQL 占用）
- [x] 大量断言适配远程行为变更：扣餐逻辑 RESERVE→CONSUME、取消策略、事件数 2→3、needName、operator 系统
- [x] 给依赖低 id seed 的测试补客户/订单/钱包 seed（V1 从 382 开始无 id 1,2,3）

**最终结果：** `mvn test` 全量 272 个测试 **0 Failures 0 Errors，BUILD SUCCESS**（集成测试连 Docker MySQL 127.0.0.1:3307）。

## 教训（已并入铁律）

`-DskipTests` 是测试腐烂的根因。CI 与 build.sh 必须真实跑测试。
