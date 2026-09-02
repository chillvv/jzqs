# AGENTS.md

jzqs（简知轻食）订餐系统。admin（商家后台 React）、backend（Spring Boot + MySQL + Flyway）、miniapp（顾客小程序）、miniapp-rider（骑手小程序）。

## 工作流总控

**所有开发任务一律先加载 `smart` 技能**。它是状态机总控，自动判断当前状态并路由到对应能力。用户不需要记住任何技能名，直接说需求即可。
- **全局唯一真源**：`C:\Users\Lenovo\.codebuddy\skills\smart\SKILL.md`（所有项目通用；2026-08-30 起不再维护项目级副本）
- jzqs 特有路径/端口/技术约定：记在 `.codebuddy/memory/MEMORY.md`，不塞进 smart

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
**Codex / Claude Code 没有 rules 注入机制**，故领域术语核心纪律并入党本文件下方「领域术语纪律」节。

## 子 agent 调用纪律（尽量外派）

> 主上下文宝贵。能独立完成且范围够紧的任务，优先派子 agent，主上下文只留结论。

1. **优先派子 agent**：大范围代码探索（跨多文件/理解结构）、可并行的独立调研/审查（code-review 双轴、多方向 research）、需 AFK 独立完成且范围紧的任务。
2. **不硬派**：简单单点查询（读一个已知文件/找一个定义）、紧密依赖主上下文的连续决策链。
3. 判断标准是"省主上下文 + 可并行"，不是"怕麻烦"。

## 项目铁律（违反=失败）

1. **证据先于断言**：禁止说"应该没问题/测试通过"，除非本轮刚跑过验证命令看到输出。
2. **业务不变量必须变成 DB 约束**：禁止"SELECT→INSERT 预检"代替 UNIQUE。
3. **CI 必须跑测试**：禁止 `-DskipTests`。
4. **状态变更同步关联表**：走状态机 helper。
5. **禁止双写/第二套实现**：单一数据源。
6. **先验证市场，再写代码**（0-1 阶段）。

## 领域术语纪律（Codex/Claude 必读；唯一权威来源 = `CONTEXT.md`）

- **Customer（客户）**= 订餐顾客（按微信 openid），**不是** `users`（后台/骑手操作账号）
- **"订单"默认指 MealSlotOrder（餐段订单）**；指 `daily_orders` 必须说"日报单"
- **MealWallet**= 餐钱包（每客户至多一个生效钱包，UNIQUE 兜底）；**WalletTransaction**= 流水
- **"派单"必须区分** DispatchBatch（批次）vs DispatchAssignment（批次里的一条指派）
- **RiderProfile**= 骑手本体；**RiderAddressBinding**= 骑手-地址绑定（按餐段拆分）
- **四张订阅表必须指名**：SubscriptionRule / CustomerDeliverySubscription / CustomerNightlySubscription / SubscriptionConfirmation / SubscriptionImportSkip
- **订单状态与派单状态是两套独立状态机**，同步走状态机 helper；订单进终态时其派单分配不得仍活跃
- 幂等必须落库（`idempotency_records`），禁止内存实现
- 新概念先登记 `CONTEXT.md` 再进代码；术语冲突以 `CONTEXT.md` 为准

## 复用优先铁律（写任何代码前必读）

1. **写任何 UI 组件/工具函数/样式/hooks 前，先搜索项目内已有实现**（搜索工具给出证据，禁止凭印象说"项目里没有"）。
2. **已有 → 直接引用，零容忍另写一套**；不够用在原组件上扩展（加 props/包一层），禁止复制一份改一版。
3. **新建模块/页面先找同模块最像的现有实现当模板**：命名、目录、结构、API 风格、样式全对齐。
4. 确认没有可复用的才新建，并**登记组件清单**（admin 组件清单见 `.codebuddy/rules/reuse-first/RULE.mdc`）。

## 技术约定

- 时区统一 Asia/Shanghai（`TimeUtils`，不用裸 `LocalDateTime.now()`）
- 幂等必须落库（`idempotency_records`），不用内存实现
- 迁移脚本只增不删；`docker-compose` 部署，`build.sh` 打包
- 测试库端口 3307（3306 被本地 MySQL 占用）
