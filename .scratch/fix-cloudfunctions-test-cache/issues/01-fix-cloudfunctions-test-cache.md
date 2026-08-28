# 01 — 修复 cloudfunctions 云函数测试 require.cache 污染

**What to build:** 让 cloudfunctions 的 Node 测试（cleanStorage / cleanupReceipts）稳定通过，消除共享 `require.cache` 的 `https` mock 交叉污染，并清理调试残留。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** medium

## 问题

两个云函数测试共享 `require.cache` 中的 `https` mock。`index.js` 在 `require` 时即捕获 `require('https')` 引用；若 `freshIndex()` 在 `installHttpsMock` 之前执行，会抓到上一个测试的旧 mock，导致断言污染 / 请求悬挂。

## Answer（2026-08-28）

- [x] 把 `cleanStorage/index.test.js` 全部 6 处 `freshIndex()` 移到 `installHttpsMock` 之后
- [x] 把 `cleanupReceipts/index.test.js` 全部 3 处 `freshIndex()` 移到 `installHttpsMock` 之后
- [x] 两个 `describe` 加 `{ concurrency: false }` 串行约束
- [x] 清理 `main` 测试残留的 `[DBG]` `process.stderr.write` 调试行
- [x] 验证：`node --test` 两个文件 **14/14 + 13/13 全通过**，无 lint

**测试结果：**

| 文件 | 通过 | 失败 |
|------|------|------|
| `cloudfunctions/cleanStorage/index.test.js` | 14 | 0 |
| `cloudfunctions/cleanupReceipts/index.test.js` | 13 | 0 |

## 范围说明

本工单仅覆盖 cloudfunctions 两个 Node 云函数测试，独立于 `fix-test-compile-errors/01`（Java 后端 272 测试）。
