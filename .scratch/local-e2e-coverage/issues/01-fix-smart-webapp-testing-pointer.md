# 01 — 修复 smart 的 webapp-testing 悬空指针

**What to build:** smart 的 S7 测试状态声明调用外接技能 `webapp-testing` 做 E2E，但该技能在 mattpocock 技能目录下并不存在，`docs/agents/external-skills.md` 也未登记。属于指针式外接（ADR-0001）的悬空指针，须改为指向项目自建的 Playwright 方案。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** medium

## 问题

- `SMART.md` 第 159 行：S7 技能写 `tdd` + `webapp-testing` + `verification-before-completion`
- `SMART.md` 第 375 行：技能栈"本地内置"列表含 `webapp-testing`
- `.codebuddy/skills/smart/SKILL.md` 的 S7 段同样引用 `webapp-testing`
- 但 `...\mattpocock_skills\skills\engineering\webapp-testing\SKILL.md` **不存在**（Test-Path = False）
- mattpocock 实际可用技能共 18 个，其中无 `webapp-testing`

## Acceptance criteria

- [x] `SMART.md` S7 段改为 `tdd` + Playwright 自建（指向 `backend/scripts/e2e/`）+ `verification-before-completion`
- [x] `SMART.md` 技能栈列表移除不存在的 `webapp-testing`，标注 E2E 走自建 Playwright
- [x] `.codebuddy/skills/smart/SKILL.md` S7 段同步修正
- [x] `docs/agents/external-skills.md` 补记：webapp-testing 不存在，E2E 用项目自建 Playwright
