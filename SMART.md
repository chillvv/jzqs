# 全生命周期开发工作流规范（v5.0 状态机 + 外接版）

> 本规范基于 jzqs 项目（2026-08 上线）的惨痛教训 + 全链路技能盘点总结。
> 核心信念：**正确性靠机制，不靠自觉。** 凡是依赖"AI 自觉想到"的，一律靠流程强制。
> 使用方式：① 技能形态 `.codebuddy/skills/smart/SKILL.md`（状态机总控，自动路由，自动生效）② 本文档（完整底稿）。

> **v5.0 变更（2026-08-28，ADR-0001）**：从 v4.0 的"内嵌"改为**指针式外接**融合 mattpocock 工程技能。
> smart 只保留我们自己的东西（铁律 / DoD / DB 约定 / 术语 / 调用纪律），
> 成熟流程以指针引用外部技能，外部升级自动生效。
> 新增 SW 超大任务规划状态、一致性审查第三轴、上下文切换决策树、Git 冲突处理。
> 外部技能登记表：`docs/agents/external-skills.md`

## 角色模型（老板与高级助理）
- **你 = 老板**：决定产品方向、技术栈、最终形态。AI 给出建议和画面，你拍板后 AI 记住并执行。
- **AI = 高级助理**：代码质量（维护性、解耦、并发、规范）自主做主，不需要你啰嗦。不确定/怕误会时主动问你。

| 决策类型 | 谁做主 |
|---------|--------|
| 产品级（技术栈/产品形态/关键取舍/数据模型/定价/上线） | **必须问老板** |
| 代码级（写码规范/解耦/可维护性/并发/测试/目录） | **AI 自主** |
| 不确定/怕误会 | **主动问** |

## 铁律（违反=失败）
1. **证据先于断言**：禁止说"应该没问题/测试通过"，除非本轮刚跑过验证命令看到输出。
2. **业务不变量必须变成 DB 约束**：禁止"SELECT→INSERT 预检"代替 UNIQUE。
3. **CI 必须跑测试**：禁止 `-DskipTests`。
4. **状态变更同步关联表**：状态机 helper 统一流转。
5. **禁止双写/第二套实现**：单一数据源。
6. **先验证市场，再写代码**：0-1 阶段没有验证证据就开发，是最大的浪费。
7. **复用优先**：写任何 UI 组件/工具函数/样式前，先搜索项目内已有实现，有则直接引用，禁止重复造轮子。
8. **外接优先（find-skills 先行）**：新增任何理念/能力/流程前，先 `find-skills` 检索成熟热门现成技能，好用直接拿来用再优化；找不到才自研。

---

## 状态机全景（不是流水线，按当前状态调技能）

```
S0想法 → S1验证 → S2需求 → S3设计 → S4骨架 → S5数据库 → S6开发 → S7测试
→ S8审查 → S9上线 → S10增长 → S11运维迭代
        ↑___________ 特殊状态：修Bug / 迭代需求（随时进入）___________↓

SW 超大任务规划（路看不清/跨多会话）→ 走完地图 → 交接到 S2
```

每个状态的定义：**触发条件 → 调用的技能 → 强制产出 → 决策点（问不问老板）**。
切换阶段时告知老板下一步；简单事项不打扰。项目完工后跳出流程，修 bug 就只走"修 Bug"状态。

### 指针式外接原则（ADR-0001）

- **内嵌**：铁律 / DoD / DB 约束规则 / 术语表 / 调用纪律 —— 这些是"我们的东西"
- **外接**：wayfinder、to-spec、to-tickets、tdd、code-review、codebase-design、diagnosing-bugs 等成熟流程
- 外部技能登记表与调用纪律：`docs/agents/external-skills.md`
- 外部技能路径缺失时**明确报出**，不静默跳过
- 调用任何外部技能前，先读登记表中该行的"调用纪律"列

---

## S0 想法
- 触发：有了一个念头
- 技能：`brainstorming`
- 产出：一句话价值主张 + 目标用户
- 决策点：要不要继续？→ 问老板

## S1 验证
- 触发：想法确认想验证
- 技能：`idea-validator` + `bmad-research` + `market-research-analysis`
- **技术向调研** → 外接 `research`（后台子 agent 查一手信源，落带引用 md）
  市场/竞品向仍用 `bmad-research`
- 产出：市场/竞品/可行性报告 + **做/不做/改方向**
- 决策点：验证结论 → 问老板拍板

## S2 需求定义
- 触发：验证通过
- 技能：`to-spec`、`product-market-fit-analysis`、`pricing`（若涉收费）
- 产出：PRD + MVP 范围（做什么/不做什么）+ 验收标准
- 决策点：MVP 边界 → 问老板

## SW 超大任务规划（wayfinder，外接）
- **触发**：需求很大且**路径不可见** —— 不是"活多"，是"不知道该怎么走"；或明确跨多个会话、含未知数
  （如整站上线、大型重构、0-1 新项目）
- **反例（不要用）**：需求已明确、拆几个 ticket 就能干 → 走 S2→S6 常规路径
- 技能：外接 `wayfinder`
- 机制：
  - **Map** `.scratch/<effort>/map.md` 五段：Destination / Notes / Decisions so far / Not yet specified / Out of scope
  - **Ticket** `issues/NN-<slug>.md`：`Type:` research|prototype|grilling|task，`Status:` claimed|resolved
  - **动手前先 claim**（claim 即锁，防并发会话重复处理）
  - **一次会话最多解决一个 ticket**（research 除外，可并行派子 agent）
  - Map 是**索引不是仓库**：Decisions so far 只存 gist + 链接，不在 map 里重述答案
  - 默认**只产决策不产交付物**（plan, don't do）
  - 迷雾（Not yet specified）**不预先切成 ticket 大小**，等前沿推进自然毕业
- 产出：清晰的路径 + 一串已解决的决策
- 决策点：**Destination（终点）先定**，它决定范围 → 问老板
- 地图走完 → **交接到 S2**（`to-spec` 收敛成可建计划）→ `to-tickets` → `implement`
  - 直接跳 `implement` 会丢掉链接的决策细节，只在任务确实变小时才这么做

## S3 设计
- 触发：需求定了
- 技能：`frontend-design` + `web-artifacts-builder`
- **设计问题在纸上定不下来时** → 外接 `prototype`
  - 逻辑/状态机分支 → 单 HTML 文件（订单状态机、分单逻辑适用）
  - UI 分支 → 多变体 URL 参数切换
  - 原型留作一手信源放 `prototype/<name>` 分支，main 只留验证过的决策
- 产出：设计 token（4-6色/字体/间距）+ 关键页面线框 + 设计方向说明
- 决策点：设计方向 → 给老板看，确认后执行
- 失败 gate：设计像默认模板 → 修改后再做

## S4 搭建骨架
- 触发：设计方向确认
- 技能：`codebase-design` + `improve-codebase-architecture`
- 产出：`ARCHITECTURE.md` + 目录结构 + CI（跑测试）+ 迁移工具启用 + 测试框架
- 决策点：技术栈选型 → 问老板

### 目录结构模板
```
project/
├── docs/                    # 所有设计文档（架构/ER/状态机/不变量/PRD）
├── backend/                 # 后端（任意语言）
│   ├── src/main/...         # 源码
│   ├── src/test/...         # 测试（与源码同树）
│   └── src/main/resources/db/migration/  # 迁移只增不删
├── frontend/                # 前端（任意框架）
├── scripts/                 # 部署/巡检脚本
├── .github/workflows/ci.yml # CI：build + test（无 -DskipTests）
└── README.md
```

## S5 数据库（防翻车核心）
- 触发：骨架就位
- 技能：`database-schema-designer` + `sql-code-review`
- 产出：schema + 迁移脚本 + **所有"至多一条"的 UNIQUE 约束** + 唯一键设计理由注释
- 决策点：数据模型 → 问老板确认

### 强制规则（9 条，血的教训）
1. 所有"至多一条"建 UNIQUE 索引（含复合唯一键）
2. 外键字段必须有约束
3. 状态字段用 ENUM/CHECK + 常量类
4. 时区统一（应用/DB/连接串三者同时区）
5. 金额用 DECIMAL/BIGINT 分，禁止 FLOAT
6. 每表带 `created_at`/`updated_at`
7. 并发预留：`version` 乐观锁或明确 `SELECT ... FOR UPDATE`
8. 唯一键设计理由写进迁移注释
9. 迁移只增不删，上线后禁改历史迁移

## S6 后端开发
- 触发：schema 定了
- 技能：`tdd` + `semgrep`
- 产出：功能代码 + 测试 + 事务/并发/幂等实现
- 自检：写操作有事务吗？并发怎么办？幂等落库了吗？状态变更同步了吗？
- 决策点：一般不问，除非影响产品行为

### 强制规则
1. 写操作必须 `@Transactional`，回答"失败回滚什么"
2. 禁止裸"SELECT→INSERT"预检（唯一约束 + 捕获 DuplicateKeyException）
3. 幂等必须落库（幂等表 + 唯一键），禁内存幂等
4. 状态变更走"状态机 helper"，同步所有关联表
5. 禁止双写/第二套实现
6. 空值语义统一（`''`/`NULL`/占位符边界写进注释）

## S7 测试
- 触发：功能写完
- 技能：`tdd` + 自建 Playwright E2E（`backend/scripts/e2e/`）+ `verification-before-completion`
  - 注：mattpocock 无 `webapp-testing`（2026-08-29 核查，Test-Path=False），E2E 走自建 Playwright；默认本地，禁默认生产
- 产出：测试通过的实际命令输出（不许只凭"应该"）
- 自检：迁移测试/状态机一致性/唯一约束/异常路径/幂等都覆盖了吗？**核心用户场景有对应测试吗？**

### 测试优先级
迁移测试 > 状态机一致性 > 唯一约束 > 异常路径 > 幂等 > happy path

### 场景驱动测试（User-Journey First）
- 写测试前先枚举**用户核心操作场景清单**（顾客怎么点餐/超限会不会被拦、后台管理员每天点什么/换区域换骑手删区域会不会报错），每个核心场景至少一个测试并指明对应关系
- **禁止只测自己写代码时想过的 happy path**（自证测试）；管理类操作（绑定/删除/改名）是事故高发区，必须覆盖关联表同步与引用保护

### 全面测试方法论（吸收 breakdown-test，ISTQB + ISO 25010）
- 写测试前调用全局技能 `breakdown-test` 生成测试策略（已登记 external-skills.md）：
  - **ISTQB 五技术**：等价类划分 / 边界值分析 / 决策表 / 状态转换 / 基于经验（探索式）
  - **四类测试覆盖**：功能 / 非功能 / 结构 / 变更相关（回归）
  - **覆盖目标**：关键路径 line>80% / branch>90%、验收标准 100%、高风险场景 100%
  - **质量门禁**：明确 entry / exit 标准
- 产出落 `.scratch/<slug>/test-strategy.md`；不用 breakdown-test 的 docs/ways-of-work 路径与 story point 估算

## S8 审查
- 触发：测试通过
- 技能：`code-review` + `sql-code-review` + `adversarial-review`（状态机/资金/跨表必加）+ `grill-me`
- 产出：至少 2 个维度的审查结论 + 修复
- 自检：只审了风格吗？一致性/并发/空值语义审了吗？

| 维度 | 技能 | 查什么 |
|------|------|--------|
| 常规质量 | `code-review`（Standards 轴） | 复杂度/SOLID/规范/Fowler 坏味道基线 |
| 规格符合 | `code-review`（Spec 轴） | 是否忠实实现了 spec/ticket |
| **一致性（本项目第三轴，必加）** | 人工 + 巡检 SQL | 见下 |
| 数据库专项 | `sql-code-review` | JOIN/空值/索引/N+1 |
| 架构一致性 | `improve-codebase-architecture` | 第二套实现/重复 |
| 红队攻击 | `adversarial-review` / `grill-me` | 反例/边界/脏数据 |
| 安全 | `semgrep` | 注入/危险函数 |

### 一致性轴（外部 `code-review` 没有的，必须补）
- [ ] 每条业务不变量都有对应 UNIQUE 约束？
- [ ] 状态变更是否走了状态机 helper 并同步所有关联表？
- [ ] 幂等是否落库（`idempotency_records`）而非内存实现？
- [ ] 有无双写/第二套实现？
- [ ] 空值语义是否统一（`''` / `NULL` / 占位符）？

`code-review` 的 Standards 与 Spec 两轴**分别汇报、不合并、不重排** —— 一轴通过不能掩盖另一轴失败。

## S9 上线
- 触发：审查通过
- 技能：`devops-engineer` + 一致性巡检 SQL
- **只有人能做的步骤**（开账号、配 CI secrets、点第三方后台、一次性迁移）→ 外接 `wizard`
  - 生成交互式脚本引导人工完成；一次性脚本跑完删除，可复用的才提交并链到 README
- 产出：部署 + 巡检结果（无脏数据）+ 回滚方案
- 决策点：上线时机 → 问老板

### 一致性巡检 SQL（固化成 `scripts/consistency-check.sql`，发版必跑）
```sql
-- 1. 重复记录
SELECT k, COUNT(*) FROM t GROUP BY k HAVING COUNT(*)>1;
-- 2. 跨表状态不一致
SELECT a.id FROM order a LEFT JOIN dispatch b ON b.order_id=a.id
WHERE a.status IN ('终态') AND b.status IN ('活跃');
-- 3. 悬空外键 / 空壳记录
SELECT * FROM child WHERE fk NOT IN (SELECT id FROM parent);
-- 4. 空值语义异常（'' 与 NULL 混用）
```

## S10 增长
- 触发：已上线
- 技能：`copywriting`/`content-strategy`/`marketing-psychology`/`seo-audit`/`ai-seo`/`social`/`cro`/`analytics`
- 产出：营销内容 + 转化优化 + 效果度量
- 决策点：营销方向/预算 → 问老板

## S11 运维迭代
- 触发：线上运行
- 技能：`diagnosing-bugs` + `web-performance-optimization` + `devops-engineer`
- 产出：Bug 修复（带回归测试）+ 性能优化
- 铁律：线上报错 24h 内补回归测试

## 特殊状态：修 Bug（随时进入）
- 技能：`diagnosing-bugs`（先建 pass/fail 信号再定位）
- 产出：根因 + 修复 + **回归测试**（红→绿验证）

## 特殊状态：迭代需求（随时进入）
- 从 S2（需求）或 S6（开发）进入，按需

## 特殊状态：Git 冲突
- 技能：外接 `resolving-merge-conflicts`
- 纪律：**按意图解决，永不 `--abort`**
  - 逐 hunk 追溯到双方的一手信源，按意图合并，必须完成操作
  - 不是"挑行"，是还原双方各自想干什么

## 领域语言维护（贯穿全程）
- 术语以 `CONTEXT.md` 为准：写 issue 标题、测试名、重构提案、hypothesis 时**用词必须对齐**
- 发现术语模糊 / 重载 / 与代码矛盾 → 外接 `domain-modeling` 当场 sharpen 并更新 `CONTEXT.md`
- 难逆转的取舍 → 提议 ADR。三个条件同时满足才写：
  ① 难逆转 ② 无上下文会让未来读者困惑 ③ 是真实权衡的产物
- 与已有 ADR 冲突 → **显式报出**，不静默覆盖

## 上下文切换（阶段边界处的决策）

阶段 = 一段工作（grilling、实现、QA）。边界处按序五问，**第一个 yes 即停**：

| 序 | 选项 | 判据 |
|----|------|------|
| 1 | **Continue** | 下阶段需要本阶段当**一手信源**，或 smart zone（~150k token）还剩够 → **默认，先排除它** |
| 2 | **Clear** | 上下文对后续完全无用 → 最便宜，旧会话仍可恢复 |
| 3 | **Handoff** | 换 harness / 换目录 / 给同事 / 中途 fork 支线（买的是可移植性） |
| 4 | **Subagent** | 能 AFK 独立完成且范围够紧 → 派出去，本会话不动 |
| 5 | **Compact** | 兜底默认项，**不是第一选择** |

**关键洞察**：除 Continue 外，每个动作都把一手信源降级为二手信源（信息有损、噪声变少、腾出空间）。
所以第 1 问必须最先问。`/compact` 放最后，因为"从摘要起步的新会话会对被压平的决策自信地犯错"。

**Subagent 的典型场景**：`code-review` 双轴并行、`research` 后台调研、wayfinder 的 research ticket。

---

## 调用决策规则（解决"不知道调用哪个"）
1. AI 根据**当前状态**自动选择技能，老板不需要说技能名
2. 老板说"修个 bug"→ AI 自动进修 Bug 状态调 `diagnosing-bugs`
3. 阶段切换时告知老板下一步；简单事项不打扰
4. 技能库 = 备选池，AI 负责编排

## 主动审查模板（新功能/新项目开场）
> "请先按工作流判断当前状态：列出实体和业务不变量（每X至多Y条）、状态机，检查 schema 是否每条不变量有唯一约束兜底。写任何业务代码前，先指出所有一致性/唯一性/并发风险，逐条说明。关键决策我问你确认。完成审查后才能写代码。"

## 记忆沉淀协议（双轨积累，越用越聪明）
- **双轨载体**：项目记忆 `.codebuddy/memory/`（MEMORY.md 长期 + YYYY-MM-DD.md 当日）存项目特有经验；全局经验库 `C:\Users\Lenovo\.codebuddy\skills\smart\LEARNINGS.md` 存跨项目通用踩坑/铁律
- **沉淀时机**：任务收尾 DoD 自查、踩坑、老板纠正、发现重复劳动时主动记（老板每次纠正都是最贵的反馈，必须沉淀）
- **归类决策**：项目特有 → 直接写项目 MEMORY.md 不打扰老板；跨项目通用 → **必须先问老板**"要入全局规范吗"，拍板才写入 LEARNINGS.md；够格升级为铁律 / RULE.mdc；老板偏好 → 写入全局记忆（update_memory）
- **两级沉淀**（借鉴 memory-merger）：经验先落草稿（当日日志）→ 成熟后合并进 MEMORY.md / LEARNINGS.md 并清理草稿；质量三标准：零知识损失 / 最小冗余 / 最大可扫描性；LEARNINGS.md 按 domain 分节
- **沉淀格式**：痛点现象 → 根因 → 规则 → 落地载体（一条一记）

## 完成定义（DoD）
```
□ 业务不变量说清（每X至多Y）
□ 数据库有对应唯一约束
□ 写操作有事务
□ 并发场景有答案（锁/乐观锁/幂等表）
□ 状态变更同步所有关联表
□ 没有双写/第二套实现
□ SQL 过了 sql-code-review
□ 有测试（异常路径/状态机/唯一约束优先）
□ CI 会跑测试（无 -DskipTests）
□ 巡检 SQL 无脏数据
□ 关键流程过了 adversarial-review
□ 前端符合设计系统 token
□ 复用已有组件（无重复造轮子；新组件已登记组件清单）
□ 核心用户场景有对应测试（用户视角，非仅代码逻辑）
□ 产品级决策问过老板
□ 有证据（命令输出）而非只凭"应该"
□ 用词符合 CONTEXT.md 术语表
□ 与 ADR 无静默冲突
```

---

## 两层工作流整合（2026-08-28 修订为指针式外接）

```
smart（总控，产品决策层：该做什么活）
    │  内嵌：铁律 / DoD / DB 约束 / 术语 / 调用纪律
    │  外接：以指针引用外部工程技能（流程细节不复制）
    ↓
mattpocock 工程工作流（工程执行层：怎么把活干漂亮）
    to-spec → triage → to-tickets → codebase-design → implement → tdd → code-review
    wayfinder（超大任务规划） / prototype / research / wizard / resolving-merge-conflicts
    ↓ 工单驱动（.scratch/ 本地 markdown）
```

- **smart 管"该做什么"**（状态机 + 老板决策 + DoD 验收 + 一致性纪律）
- **mattpocock 管"怎么做好"**（seam 设计、TDD、审查、工单流转、决策地图）
- **融合方式**：指针式外接（ADR-0001）。外部技能升级自动生效，smart 永不腐烂成过期副本
- 登记表与调用纪律：`docs/agents/external-skills.md`

### 已完成的配置（2026-08-28）
| 文件 | 作用 |
|------|------|
| `docs/agents/issue-tracker.md` | 本地 markdown tracker 规范 + wayfinder 操作 |
| `docs/agents/triage-labels.md` | 五个 triage 角色 + 本项目扩展状态 |
| `docs/agents/domain.md` | single-context 领域文档布局与消费规则 |
| `docs/agents/external-skills.md` | **外部技能登记表（路径 + 触发点 + 调用纪律 + 隐含前提）** |
| `AGENTS.md` | 项目入口（CodeBuddy 兼容模式自动全文加载）+ `## Agent skills` 块 |
| `CONTEXT.md` | 领域术语表（31 张表提炼的概念与邻界区分）**唯一权威来源** |
| `docs/adr/0001-...` | 指针式外接架构决策 |
| `.scratch/**/issues/NN-*.md` | 工单已迁移至 mattpocock 规范布局 |
| `.codebuddy/rules/domain-context/RULE.mdc` | 术语**加载桥接**（alwaysApply 常驻） |
| `.codebuddy/rules/smart-workflow/RULE.mdc` | 工作流路由（智能体请求型，按需加载） |

### 加载机制（关键，别踩坑）

CodeBuddy **只自动加载** `CODEBUDDY.md`，以及无 `CODEBUDDY.md` 时的 `AGENTS.md`（兼容模式）。
`CONTEXT.md`、`CLAUDE.md` **都不会自动加载** —— 但 mattpocock 外部技能全都假定 `CONTEXT.md` 已读。

故采用**桥接**模式：`CONTEXT.md` 保持唯一权威，`.codebuddy/rules/domain-context/RULE.mdc`
（`alwaysApply: true`）镜像其术语常驻上下文。术语变更时两者同步，不一致以 `CONTEXT.md` 为准。

**规则类型分工**（官方建议 always 规则控制在 3-5 个）：
- `domain-context`：always（术语是每次写代码都要用的基准）
- `smart-workflow`：**智能体请求型**（只加载名称+描述，判断相关才读原文，避免每会话占上下文）

## 完整技能栈（2026-08-28 修订：内置 / 外接二分）

> 指针式外接后，技能分两类：**内置**（本地技能/内嵌纪律）与**外接**（指针引用 mattpocock）。
> 外接技能的路径与调用纪律见 `docs/agents/external-skills.md`。

### 总控（2 个，本地）
| 技能 | 作用 |
|------|------|
| `smart` | 状态机自动路由总控（本规范技能形态） |
| `new-project` | 一键初始化新项目骨架（目录+铁律+smart+.scratch） |

### 外接：mattpocock 工程工作流（16 个）
根路径 `C:\Users\Lenovo\.codebuddy\plugins\marketplaces\mattpocock_skills\skills\engineering\`

| 分类 | 技能 |
|------|------|
| 需求→工单 | `to-spec`、`to-tickets`、`triage` |
| 规划 | **`wayfinder`**（超大任务决策地图，v5.0 新增） |
| 设计/验证 | `codebase-design`、`prototype`（v5.0 新增）、`domain-modeling` |
| 实施 | `implement`、`tdd` |
| 审查 | `code-review` |
| 排障 | `diagnosing-bugs`、`resolving-merge-conflicts`（v5.0 新增） |
| 调研/引导 | `research`（v5.0 新增）、`wizard`（v5.0 新增） |
| 架构 | `improve-codebase-architecture` |

**说明**：`grill-with-docs`（= grilling + domain-modeling）在有 repo 时严格优于 `grill-me`，
故需求澄清走 `grill-with-docs`，`grill-me` 仅用于无工作目录的场景。

### 本地内置：质量防线（5 个）
`verification-before-completion`（证据铁律）、`adversarial-review`（红队）、`semgrep`（静态扫描）、`database-schema-designer`（数据库）、`sql-code-review`（SQL审查）

### 本地内置：产品/营销（12 个）
`idea-validator`、`bmad-research`、`copywriting`、`content-strategy`、`marketing-psychology`、`seo-audit`、`ai-seo`、`social`、`cro`、`analytics`、`pricing`、`launch`

### 本地内置：前端设计（3 个）
`frontend-design`、`shadcn`、`tailwind-design-system`

### 本地内置：运维/测试（2 个）
`devops-engineer`、`web-performance-optimization`

> **已移除 `webapp-testing`**（2026-08-29 核查）：该技能在 mattpocock 目录下并不存在，属悬空指针（Test-Path=False）。
> E2E 改由项目自建 Playwright 承担，脚本位于 `backend/scripts/e2e/browser/`。

### 工具备选（10 个）
`brainstorming`、`product-market-fit-analysis`、`user-research-analysis`、`find-skills`、`agent-browser`、`web-artifacts-builder`、`writing-for-agents`、`folder-structure-blueprint-generator`、`transaction-management`、`handoff`

### v5.0 决策记录：恢复与剔除
**恢复接入（此前误删）**：
- `wayfinder` —— 唯一的超大任务规划能力，v4.0 删除导致大工程无章可循
- `setup-matt-pocock-skills` —— 一次性配置，是其它技能能读 tracker 的**前置条件**，非日常技能
- `ask-matt` —— 其主流程与 PHASE-BOUNDARIES 已并入 smart 的 S4-S8 与"上下文切换"章节，不再单独保留

**继续剔除（合理）**：
`code-reviewer`（与 `code-review` 重复）、`vercel-react-best-practices`、`vercel-composition-patterns`、`hallmark`、`market-research-analysis`

### 待装（可选）
`sentry-cli`（错误监控）：`npx skills add https://cli.sentry.dev -g -y`
