# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — jzqs 领域术语表（Order / Dispatch / MealWallet / RiderAddressBinding …）
- **`docs/adr/`** — read ADRs that touch the area you're about to work in.

本项目为 **single-context** 布局（admin / backend / miniapp / miniapp-rider 共享同一套领域语言），因此无 `CONTEXT-MAP.md`。

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `grill-with-docs` and `improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## 加载机制（CodeBuddy 特有，重要）

`CONTEXT.md` 是 **mattpocock 约定，CodeBuddy 不会自动加载它**。外部技能（tdd / code-review / domain-modeling / improve-codebase-architecture）都假定它已被读取 —— 这个隐含前提在 CodeBuddy 下不成立。

**桥接方案**：`.codebuddy/rules/domain-context/RULE.mdc`（`alwaysApply: true`）把领域术语常驻上下文。

| 文件 | 角色 | 加载方式 |
|------|------|---------|
| `CONTEXT.md` | **唯一权威来源**（术语在此维护） | 不自动加载，需显式读取 |
| `.codebuddy/rules/domain-context/RULE.mdc` | 加载桥接（内容镜像） | alwaysApply，自动常驻 |
| `AGENTS.md` | 项目入口 | CodeBuddy 兼容模式自动全文加载 |

**维护纪律**：
- 术语变更 → 改 `CONTEXT.md`（权威），并同步 `RULE.mdc`（镜像）
- 两者不一致时以 `CONTEXT.md` 为准，并报告该不一致
- `RULE.mdc` 顶部已声明其桥接性质，避免被误当成权威源

**推论**：任何外部技能假定"自动存在"的文件，都需检查 CodeBuddy 是否真会加载，必要时加规则桥接。这是指针式外接的隐含前提成本。

## File structure

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-*.md
│   └── 0002-*.md
├── admin/
├── backend/
├── miniapp/
└── miniapp-rider/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_
