# 01 — 随机测试库永不删除，持续堆积

**What to build:** 让 `@SpringBootTest` 随机创建的 `jzqs_test_<uuid>` 库在测试结束后被自动清理
（或改为固定库名 + 每次重建），避免长期堆积。

**Blocked by:** None — can start immediately

**Status:** todo

**类型:** chore
**优先级:** medium

## 证据（2026-09-10）

`backend/src/test/resources/application.yml`：

```yaml
url: jdbc:mysql://127.0.0.1:3307/jzqs_test_${random.uuid}?createDatabaseIfNotExist=true&...
```

每个 Spring 测试上下文都会 `createDatabaseIfNotExist` 建一个新库，**代码里没有任何 DROP**。
一次盘点 `information_schema.schemata` 里匹配 `jzqs_test_%` 的库有 **120+ 个**
（当天晚些被手工清理，但机制仍在，跑一次全量测试就会再堆一批）。

## 影响

- 占磁盘：每个库一整套 Flyway schema（约 2~3 MB），上百个就是几百 MB
- `SHOW DATABASES` 噪声极大，排查时找不到北
- 拖慢 MySQL 启动与备份

## 候选方案

- **A（推荐）**：注册 `ContextClosedEvent` 监听器，在上下文关闭时 DROP 本上下文的随机库。
  保留上下文间隔离，测试 JVM 退出即自动清理。
- B：改用固定库名 + Flyway clean。代价：多个测试上下文并行时会互相干扰
  （本地同时跑两个 `mvn test` 会冲突，2026-09-10 就发生过）。
- C：只在 CI 里跑完 drop，本地靠手工——治标不治本。

## Acceptance criteria

- [ ] 跑完 `mvn test` 后，`jzqs_test_%`（随机 uuid 那批）数量回到跑之前
- [ ] 不破坏 `BaseDbIntegrationTest`（它依赖固定库 `jzqs_test`，不能一起删）
- [ ] 并行跑测试不互相干扰
- [ ] 清理失败只打 WARN，不让测试因此报错
