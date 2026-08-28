# AGENTS.md

jzqs（简知轻食）订餐系统。admin（商家后台 React）、backend（Spring Boot + MySQL + Flyway）、miniapp（顾客小程序）、miniapp-rider（骑手小程序）。

## 工作流总控

**所有开发任务一律先加载 `smart` 技能**（`.codebuddy/skills/smart/SKILL.md`）。它是状态机总控，自动判断当前状态并路由到对应能力。用户不需要记住任何技能名，直接说需求即可。

## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/<feature-slug>/` — one `spec.md` plus one file per ticket at `issues/NN-<slug>.md`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical mattpocock triage roles, used as-is, plus two project-local states (`claimed`, `in-progress`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

**加载方式（重要）**：`CONTEXT.md` 是 mattpocock 约定，CodeBuddy **不会自动加载**它。
本项目通过 `.codebuddy/rules/domain-context/RULE.mdc`（alwaysApply）把领域术语常驻上下文。
`CONTEXT.md` 仍是**唯一权威来源**，规则文件只做加载桥接。修改术语时改 `CONTEXT.md`。

## 项目铁律（违反=失败）

1. **证据先于断言**：禁止说"应该没问题/测试通过"，除非本轮刚跑过验证命令看到输出。
2. **业务不变量必须变成 DB 约束**：禁止"SELECT→INSERT 预检"代替 UNIQUE。
3. **CI 必须跑测试**：禁止 `-DskipTests`。
4. **状态变更同步关联表**：走状态机 helper。
5. **禁止双写/第二套实现**：单一数据源。
6. **先验证市场，再写代码**（0-1 阶段）。

## 技术约定

- 时区统一 Asia/Shanghai（`TimeUtils`，不用裸 `LocalDateTime.now()`）
- 幂等必须落库（`idempotency_records`），不用内存实现
- 迁移脚本只增不删；`docker-compose` 部署，`build.sh` 打包
- 测试库端口 3307（3306 被本地 MySQL 占用）
