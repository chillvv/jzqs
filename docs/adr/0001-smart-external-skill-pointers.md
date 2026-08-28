# ADR-0001: smart 以指针式外接方式融合 mattpocock 技能

- **Date:** 2026-08-28
- **Status:** Accepted

## Context

项目已有自主工作流总控 `smart`（12 状态机 + 6 条铁律 + DoD 13 条），覆盖从想法到运维的全生命周期。
另有一套 mattpocock 工程技能集（`C:\Users\Lenovo\.codebuddy\plugins\marketplaces\mattpocock_skills\skills\engineering\`），
在工程执行段（规格/工单/TDD/审查/领域建模）比 smart 更深，且独有 wayfinder（超大任务规划）、prototype、wizard、research 等能力。

需要决定两者如何共存。候选方案：
1. 内嵌复制 —— 把 mattpocock 流程全文抄进 smart
2. 指针式外接 —— smart 只存"何时调用 + 调用纪律"，流程指向外部技能路径
3. 混合 —— 纪律内嵌、流程外接

## Decision

**采用指针式外接。** smart 内只保留：
- 触发条件（什么情况下调哪个外部技能）
- 调用纪律（本项目铁律、CONTEXT.md 词汇约定、tracker 布局等外部技能不知道的东西）
- 外部技能的路径指针

流程细节一律不复制，由外部技能自己持有。

## Consequences

**正面：**
- 外部技能升级时 smart 自动受益，永不腐烂成过期副本
- smart 保持精简，专注"该做什么活"与"产品级决策"
- 无双重维护成本

**负面 / 代价：**
- 跨机器使用需该路径存在。缓解：在 `docs/agents/external-skills.md` 集中登记路径，缺失时明确报出而非静默跳过
- 外部技能不可用时该能力降级，需人工介入

## 配套决策

- issue tracker 用**本地 markdown `.scratch/`**，不用 GitHub Issues（个人项目，零外部依赖，离线可用）
- 采用 mattpocock 本地 tracker 布局：`spec.md` + `issues/NN-<slug>.md`（一 ticket 一文件，禁止合并）
- 领域文档为 **single-context**：根 `CONTEXT.md` + `docs/adr/`
- 冲突解决采用 `resolving-merge-conflicts` 原则：**按意图解决，永不 `--abort`**
- 接入的外部能力：wayfinder、prototype、wizard、research
