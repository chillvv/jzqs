# Vibe Coding 全生命周期工作流规范（v4.0 状态机版）

> 本规范基于 jzqs 项目（2026-08 上线）的惨痛教训 + 全链路技能盘点总结。
> 核心信念：**正确性靠机制，不靠自觉。** 凡是依赖"AI 自觉想到"的，一律靠流程强制。
> 使用方式：① 技能形态 `.codebuddy/skills/smart/SKILL.md`（状态机总控，自动路由，自动生效）② 本文档（完整底稿）。

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

---

## 状态机全景（不是流水线，按当前状态调技能）

```
S0想法 → S1验证 → S2需求 → S3设计 → S4骨架 → S5数据库 → S6开发 → S7测试
→ S8审查 → S9上线 → S10增长 → S11运维迭代
        ↑___________ 特殊状态：修Bug / 迭代需求（随时进入）___________↓
```

每个状态的定义：**触发条件 → 调用的技能 → 强制产出 → 决策点（问不问老板）**。
切换阶段时告知老板下一步；简单事项不打扰。项目完工后跳出流程，修 bug 就只走"修 Bug"状态。

---

## S0 想法
- 触发：有了一个念头
- 技能：`brainstorming`
- 产出：一句话价值主张 + 目标用户
- 决策点：要不要继续？→ 问老板

## S1 验证
- 触发：想法确认想验证
- 技能：`idea-validator` + `bmad-research` + `market-research-analysis`
- 产出：市场/竞品/可行性报告 + **做/不做/改方向**
- 决策点：验证结论 → 问老板拍板

## S2 需求定义
- 触发：验证通过
- 技能：`to-spec`、`product-market-fit-analysis`、`pricing`（若涉收费）
- 产出：PRD + MVP 范围（做什么/不做什么）+ 验收标准
- 决策点：MVP 边界 → 问老板

## S3 设计
- 触发：需求定了
- 技能：`frontend-design` + `web-artifacts-builder`
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
- 技能：`tdd` + `webapp-testing` + `verification-before-completion`
- 产出：测试通过的实际命令输出（不许只凭"应该"）
- 自检：迁移测试/状态机一致性/唯一约束/异常路径/幂等都覆盖了吗？

### 测试优先级
迁移测试 > 状态机一致性 > 唯一约束 > 异常路径 > 幂等 > happy path

## S8 审查
- 触发：测试通过
- 技能：`code-review` + `sql-code-review` + `adversarial-review`（状态机/资金/跨表必加）+ `grill-me`
- 产出：至少 2 个维度的审查结论 + 修复
- 自检：只审了风格吗？一致性/并发/空值语义审了吗？

| 维度 | 技能 | 查什么 |
|------|------|--------|
| 常规质量 | `code-review` | 复杂度/SOLID/规范 |
| 数据库专项 | `sql-code-review` | JOIN/空值/索引/N+1 |
| 架构一致性 | `improve-codebase-architecture` | 第二套实现/重复 |
| 红队攻击 | `adversarial-review` / `grill-me` | 反例/边界/脏数据 |
| 安全 | `semgrep` | 注入/危险函数 |

## S9 上线
- 触发：审查通过
- 技能：`devops-engineer` + 一致性巡检 SQL
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

---

## 调用决策规则（解决"不知道调用哪个"）
1. AI 根据**当前状态**自动选择技能，老板不需要说技能名
2. 老板说"修个 bug"→ AI 自动进修 Bug 状态调 `diagnosing-bugs`
3. 阶段切换时告知老板下一步；简单事项不打扰
4. 技能库 = 备选池，AI 负责编排

## 主动审查模板（新功能/新项目开场）
> "请先按工作流判断当前状态：列出实体和业务不变量（每X至多Y条）、状态机，检查 schema 是否每条不变量有唯一约束兜底。写任何业务代码前，先指出所有一致性/唯一性/并发风险，逐条说明。关键决策我问你确认。完成审查后才能写代码。"

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
□ 产品级决策问过老板
```

---

## 两层工作流整合（2026-08-27 定稿）

```
smart（总控，产品决策层：该做什么活）
    ↓ 进入 S4-S8 后调用
mattpocock 工程工作流（工程执行层：怎么把活干漂亮）
    to-spec → triage → to-tickets → codebase-design → implement → tdd → code-review → grill-me
    ↓ 工单驱动（.scratch/ 本地 markdown）
```

- **smart 管"该做什么"**（状态机 + 老板决策 + DoD 验收）
- **mattpocock 管"怎么做好"**（seam 设计、TDD、审查、工单流转）
- 需要 `.scratch/` 工单机制时，跑 `setup-matt-pocock-skills` 配置（issue tracker 用本地 markdown）

## 完整技能栈（精简后 45 个，2026-08-27 最终版）

### 总控（1 个）
| 技能 | 作用 |
|------|------|
| `smart` | 状态机自动路由总控（本规范技能形态） |
| `new-project` | 一键初始化新项目骨架（目录+铁律+smart+.scratch） |

### 框架核心（6 个，mattpocock 工程工作流）
`tdd`、`codebase-design`、`diagnosing-bugs`、`grill-me`、`domain-modeling`、`improve-codebase-architecture`

### 质量防线（5 个）
`verification-before-completion`（证据铁律）、`adversarial-review`（红队）、`semgrep`（静态扫描）、`database-schema-designer`（数据库）、`sql-code-review`（SQL审查）

### 产品/营销（12 个）
`idea-validator`、`bmad-research`、`copywriting`、`content-strategy`、`marketing-psychology`、`seo-audit`、`ai-seo`、`social`、`cro`、`analytics`、`pricing`、`launch`

### 前端设计（3 个）
`frontend-design`、`shadcn`、`tailwind-design-system`

### 运维/测试（3 个）
`devops-engineer`、`webapp-testing`、`web-performance-optimization`

### mattpocock 配套（4 个）
`to-spec`、`to-tickets`、`triage`、`handoff`

### 工具备选（10 个）
`brainstorming`、`product-market-fit-analysis`、`user-research-analysis`、`find-skills`（安装：`npx skills add https://github.com/vercel-labs/skills --skill find-skills`）、`agent-browser`、`web-artifacts-builder`、`writing-for-agents`、`folder-structure-blueprint-generator`、`transaction-management`、`implement`

### 已删（9 个，2026-08-27）
`grill-with-docs`、`code-reviewer`、`setup-matt-pocock-skills`、`vercel-react-best-practices`、`vercel-composition-patterns`、`hallmark`、`market-research-analysis`、`ask-matt`、`wayfinder`

### 待装（可选）
`sentry-cli`（错误监控）：`npx skills add https://cli.sentry.dev -g -y`
