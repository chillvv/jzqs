# 04 — 骑手端测试补充与全量验证

**What to build:** 为本次三项优化补充/更新 `miniapp-rider/tests` 下的 node 测试，
并跑全量 `node --test` 确保无回归。

**Blocked by:** 01, 02, 03

**Status:** done

**类型:** task
**优先级:** high

## Acceptance criteria

- [x] `request-auth.test.js`：uploadFile 服务路由头断言 + 瞬时失败重试成功用例 + 401 不重试用例
- [x] 新增 `map-service.test.js`：坐标直开 / 地理编码成功 / 失败兜底复制 三条路径
- [x] `order-detail-experience.test.js`：独立备注卡片结构断言
- [x] `node --test` 全量通过（含既有 13 个测试文件）
