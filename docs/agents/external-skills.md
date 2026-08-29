# 外部技能登记（External Skill Registry）

smart 采用**指针式外接**（ADR-0001）：smart 只存"何时调用 + 调用纪律"，流程细节由外部技能自己持有。

**根路径**：`C:\Users\Lenovo\.codebuddy\plugins\marketplaces\mattpocock_skills\skills\engineering\`

> 若该路径不存在，明确告知用户，不要静默跳过该能力。

## 隐含前提（CodeBuddy 下不成立的假设，必须桥接）

指针式外接的隐藏成本：**外部技能假定某些文件"自动存在"，但 CodeBuddy 不加载它们。**

| 文件 | 外部技能是否假定其存在 | CodeBuddy 是否自动加载 | 处理 |
|------|---------------------|---------------------|------|
| `CONTEXT.md` | **是**（tdd / code-review / domain-modeling / improve-codebase-architecture 都会读） | **否** | 已用 `.codebuddy/rules/domain-context/RULE.mdc`（alwaysApply）桥接 |
| `docs/adr/` | 是 | 否（需显式读取） | 按需读取；`AGENTS.md` 与 smart 内已指向 |
| `AGENTS.md` | 是（`## Agent skills` 块） | **是**（兼容模式，因无 `CODEBUDDY.md`） | 无需处理 |
| `CLAUDE.md` | 部分技能读 | **否**（CodeBuddy 只认 `CODEBUDDY.md`/`AGENTS.md`） | 本项目不创建 `CLAUDE.md` |
| `docs/agents/issue-tracker.md` | 是 | 否 | 由 smart 显式指向 |

**新增外部技能时，先问它假定哪些文件自动存在，逐个验证并桥接。**

## 登记表

| 能力 | 路径 | smart 中的触发点 | 调用纪律（外部技能不知道的） |
|------|------|-----------------|---------------------------|
| **wayfinder** | `wayfinder/SKILL.md` | SW 超大任务规划 | 见下"wayfinder 纪律" |
| **prototype** | `prototype/SKILL.md` | S3 设计 / S6 开发前验证 | 逻辑分支优先用于订单状态机、分单逻辑；原型分支 `prototype/<name>`，不在 main |
| **research** | `research/SKILL.md` | S1 验证 / S6 技术调研 | 技术向调研用它；市场/竞品向仍用 `bmad-research` |
| **wizard** | `wizard/SKILL.md` | S9 上线 / S11 运维 | 一次性脚本放 `scripts/`，跑完删除；可复用的才提交并链到 README |
| **to-spec** | `to-spec/SKILL.md` | S2 需求定义 | seam 必须先用 `codebase-design` 词汇表达；DB 不变量必须列出 |
| **to-tickets** | `to-tickets/SKILL.md` | S2 → S6 之间 | vertical slice 必须端到端含 DB 迁移；prefactoring 优先 |
| **triage** | `triage/SKILL.md` | 工单进入时 | 只 triage 外部来的 issue；`to-tickets` 产出的 ticket 不再 triage |
| **implement** | `implement/SKILL.md` | S6 开发 | 每 ticket 一个干净上下文；收尾必跑 `code-review` |
| **tdd** | `tdd/SKILL.md` | S6 开发 / S7 测试 | seam 先与用户确认再写测试；测试优先级：迁移 > 状态机 > 唯一约束 > 异常 > 幂等 > happy path |
| **code-review** | `code-review/SKILL.md` | S8 审查 | 两轴（Standards/Spec）之外，本项目**必加**第三轴：一致性（DB 约束/状态同步/幂等） |
| **codebase-design** | `codebase-design/SKILL.md` | S4 骨架 / S6 开发 | 深模块词汇（module/interface/seam/adapter/depth/leverage/locality） |
| **domain-modeling** | `domain-modeling/SKILL.md` | 全程 | 术语以 `CONTEXT.md` 为准；新概念先登记再进代码 |
| **diagnosing-bugs** | `diagnosing-bugs/SKILL.md` | 修 Bug / S11 | 先建红→绿信号再定位；修复必带回归测试 |
| **improve-codebase-architecture** | `improve-codebase-architecture/SKILL.md` | S4 / S11 | HTML 报告写系统 temp，不落 repo |
| **grill-with-docs** | `grill-with-docs/SKILL.md` | S0-S2 需求澄清 | 内部会调 `grilling` + `domain-modeling` 两次；有 repo 时优于 `grill-me` |
| **resolving-merge-conflicts** | `resolving-merge-conflicts/SKILL.md` | 任何 git 冲突 | **按意图解决，永不 `--abort`** |
| **memory-merger** | `~\ .agents\skills\memory-merger\SKILL.md`（**全局技能**，不在 mattpocock 目录，2026-08-29 已装） | 记忆沉淀 / 任何任务收尾 | 借鉴其**两级沉淀**（经验先落记忆文件草稿 → 成熟后合并进指令文件并清理）+ **domain 化归类** + **合并前用户审批**；落地路径用我们自己的 `.codebuddy/memory`（项目）/ LEARNINGS.md（全局），不用它的 vscode-userdata 约定。触发 `/memory-merger >domain [scope]`，但 scope 概念映射到我们的双轨 |
| **breakdown-test** | `~\ .agents\skills\breakdown-test\SKILL.md`（**全局技能**，2026-08-29 已装，Snyk Low Risk） | S7 测试策略生成 | **ISTQB 五技术**（等价类划分/边界值分析/决策表/状态转换/基于经验探索）+ **ISO 25010 质量矩阵**（8 质量特性优先级）+ **四类测试覆盖**（功能/非功能/结构/回归）+ **覆盖目标**（关键路径 line>80%/branch>90%、验收标准 100%、高风险场景 100%）+ **质量门禁**（entry/exit）。产出落 `.scratch/<slug>/test-strategy.md`；**不用**它的 `docs/ways-of-work/` 路径与 story point 估算 |

### 不存在的外部能力（悬空指针排查结果）

以下能力曾在 smart / SMART.md 中被引用，但**在 mattpocock 目录下并不存在**，已从指针中移除，**勿再引用**：

| 名称 | 核查日期 | 处置 |
|------|---------|------|
| `webapp-testing` | 2026-08-29 | 不存在（Test-Path=False）。E2E 改用项目自建 Playwright，脚本在 `backend/scripts/e2e/browser/` |

**新增任何指针前，必须先 `Test-Path` 验证路径存在**，否则即为悬空指针，违反本表"路径不存在须明确报出"的纪律。

## wayfinder 纪律（本地 markdown 版）

- Map: `.scratch/<effort>/map.md`，五段固定：Destination / Notes / Decisions so far / Not yet specified / Out of scope
- Ticket: `.scratch/<effort>/issues/NN-<slug>.md`，带 `Type:`（research|prototype|grilling|task）与 `Status:`（claimed|resolved）
- Blocking 用 `Blocked by: NN, NN` 文本行；frontier = open + 依赖全 resolved + 未 claimed
- **动手前先 claim**（置 `Status: claimed`），claim 即锁
- **一次会话最多解决一个 ticket**（research 除外，可并行派子 agent）
- Map 是索引不是仓库：Decisions so far 只存 gist + 链接，不在 map 里重述答案
- 默认**只产决策不产交付物**（plan, don't do）；想直接建需在 Notes 里 override
- 迷雾（Not yet specified）不预先切成 ticket 大小，等前沿推进自然毕业

## 上下文切换决策树（来自 ask-matt/PHASE-BOUNDARIES.md）

阶段边界处按序五问，第一个 yes 即停：

1. **Continue** — 下阶段需要本阶段当一手信源，或 smart zone（~150k token）还剩够 → **默认继续，先排除它**
2. **/clear** — 上下文对后续完全无用 → 最便宜，旧会话仍可恢复
3. **/handoff** — 换 harness / 换目录 / 给同事 / 中途 fork 支线（买的是可移植性）
4. **Subagent** — 能 AFK 独立完成且范围够紧 → 派出去，本会话不动
5. **/compact** — 兜底默认项，**不是第一选择**

除 Continue 外，每个动作都把一手信源降级为二手信源（信息有损）。故第 1 问必须最先问。
