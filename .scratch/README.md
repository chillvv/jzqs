# 工单机制（issue tracker，本地 markdown）

Issues and specs for this repo live as markdown files in `.scratch/`。
规范来源：`docs/agents/issue-tracker.md`（mattpocock `setup-matt-pocock-skills` 生成）。

## 目录规范

```
.scratch/
├── <feature-slug>/
│   ├── spec.md                      ← 该需求的规格（to-spec 产出）
│   └── issues/
│       ├── 01-<slug>.md             ← 每个 ticket 一个文件，从 01 编号
│       ├── 02-<slug>.md
│       └── ...
└── <effort>/                        ← wayfinder 大型任务
    ├── map.md                       ← 地图：Destination/Notes/Decisions/迷雾/Out of scope
    └── issues/
        ├── 01-<slug>.md             ← 决策 ticket（Type: research|prototype|grilling|task）
        └── ...
```

**禁止**：把一个 feature 的所有 ticket 合并进单个文件。一 ticket 一文件。

## Ticket 文件模板

```markdown
# <NN> — <Ticket title>

**What to build:** 从用户视角描述的端到端行为，不是分层实现清单。

**Blocked by:** 依赖的 ticket 编号/标题，或 "None — can start immediately"

**Status:** needs-triage | needs-info | ready-for-agent | ready-for-human | wontfix | in-progress | done

**类型:** bug | enhancement
**优先级:** high | medium | low

## Acceptance criteria

- [ ] 验收条件 1
- [ ] 验收条件 2
```

## wayfinder ticket 附加字段

```
**Type:** research | prototype | grilling | task
**Status:** claimed | resolved
```

- **Claim**：动手前先置 `Status: claimed`（assignee 即锁，防并发会话重复处理）
- **Resolve**：在 `## Answer` 段追加答案，置 `Status: resolved`，并把 gist+链接追加到 `map.md` 的 Decisions-so-far
- **Frontier**：扫描 `issues/` 中 open + 无未 resolved 依赖 + 未 claimed 的文件，编号最小者优先

## 状态流转

```
needs-triage → needs-info（要更多信息）
             → ready-for-agent（AI 可以做了）→ in-progress → done
             → ready-for-human（需要人工）
             → wontfix（不做了）
```

## 常用指令

- "**记个工单**" → 创建 `.scratch/<slug>/issues/01-<slug>.md`
- "**看下有哪些工单**" → 列出所有 issues 及状态
- "**看下 frontier**" → 列出可立即开工的 ticket（依赖已 resolved 且未 claimed）
- 开发前 → 读工单规格 → `codebase-design` → `tdd`

## 当前工单

| 工单 | 状态 | 说明 |
|------|------|------|
| [create-consistency-check-sql/01](create-consistency-check-sql/issues/01-consistency-check-sql.md) | ready-for-agent | 上线前一致性巡检 SQL |
| [fix-test-compile-errors/01](fix-test-compile-errors/issues/01-fix-test-compile-errors.md) | resolved | 测试编译修复，272 测试全绿 |
| [fix-cloudfunctions-test-cache/01](fix-cloudfunctions-test-cache/issues/01-fix-cloudfunctions-test-cache.md) | done | cloudfunctions 云函数测试 require.cache 污染修复（Node 测试） |
| [local-e2e-coverage/01](local-e2e-coverage/issues/01-fix-smart-webapp-testing-pointer.md) | done | 修复 smart 的 `webapp-testing` 悬空指针（该技能不存在） |
| [local-e2e-coverage/02](local-e2e-coverage/issues/02-localize-admin-e2e.md) | in-progress | admin E2E 本地化：脚本已改造，浏览器跑通待起服务 |
| [local-e2e-coverage/03](local-e2e-coverage/issues/03-cross-end-e2e-local-db.md) | done | 跨端 E2E 连本地 3307（CrossEndFlowE2ETest 3/3 通过） |
| [order-note-merge/01](order-note-merge/issues/01-merge-note-projection-with-comma.md) | done | 三端备注投影改「列 ∪ 快照」去重 + 逗号拼接 |
| [order-note-merge/02](order-note-merge/issues/02-snapshot-preserve-one-time-notes.md) | done | 订单级商家备注收敛成单一落点 + 修错列 bug + V30 迁移 |
| [rider-miniapp-optimization/01](rider-miniapp-optimization/issues/01-upload-retry.md) | done | 骑手回执上传补退避重试 + 服务路由头 |
| [rider-miniapp-optimization/02](rider-miniapp-optimization/issues/02-navigation-address.md) | done | 导航按客户地址：后端地理编码代理 + 分层导航 |
| [rider-miniapp-optimization/03](rider-miniapp-optimization/issues/03-order-detail-notes.md) | done | 订单详情备注独立卡片醒目展示 |
| [rider-miniapp-optimization/04](rider-miniapp-optimization/issues/04-rider-tests.md) | done | 骑手端测试补充与全量验证（进度见 [progress.md](rider-miniapp-optimization/progress.md)） |
