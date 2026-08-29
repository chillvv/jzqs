# 02 — admin E2E 本地化改造（默认本地 + 业务链路 + DB 断言 + 接 CI）

**What to build:** 把 `backend/scripts/e2e/browser/admin-e2e.spec.js` 从"默认打生产环境的冒烟脚本"，改造成"默认打本地、含真实业务链路与数据库断言、可接入 CI"的端到端测试。

**Blocked by:** None — can start immediately

**Status:** in-progress

**类型:** bug
**优先级:** high

## 问题

- 默认 `BASE_URL = https://jzqs.top`（**生产环境**），本地跑需显式覆盖，误跑会打生产站
- 只断言"页面文本长度 > 80"的冒烟，无业务链路（不下单 / 不派单 / 不退款）
- 无数据库断言
- 未接 CI，需手动 `npx playwright test` 且先装 chromium

## Acceptance criteria

- [x] 默认 `BASE_URL` 改为 `http://localhost:5173`，生产地址须显式传环境变量
- [x] 生产保护：`BASE_URL` 命中 `jzqs.top` 且未设 `E2E_ALLOW_PRODUCTION=true` 时**直接抛错拒绝运行**
- [x] 补数据库断言（`E2E_DB_ENABLED=true` 生效，未装 mysql2 则自动跳过）：
      - 终态订单不得仍挂活跃派单分配（跨表状态同步）
      - 钱包不得超扣（`consumed_meals > total_meals`）
- [x] 页面 JS 错误收集（`pageerror` + `console.error`）并在 afterAll 打印
- [x] 后端测试接进 CI：`.github/workflows/deploy.yml` 新增 `test` job（mysql service 映射 3307，
      跑 `mvn -B test`），`deploy` 加 `needs: test`（**修复 CI 不跑测试违反铁律 3 的问题**）
- [x] 本地跑通浏览器 E2E（admin dev 5173 + backend 8081 + Playwright 1.62.1 + chromium，2026-08-29）
- [ ] Playwright 浏览器 E2E 接 CI（**待办**：需起全栈 docker-compose + 装 chromium，维护成本高）

## 本地跑通验证（2026-08-29，实际命令输出）

```
npx playwright test admin-e2e.spec.js   # 设 E2E_DB_ENABLED=true 时含 DB 一致性断言
✅ 登录成功进入看板
✅ 8 个核心页面加载（订单/派单×3/顾客/售后/菜单/操作日志）
✅ 关键操作链路（订单/顾客/派单）
🔍 终态订单挂活跃派单 = 0，超扣钱包 = 0，JS 错误 = 0
3 passed (28.9s)
```

**本地跑通步骤（已在本地验证成功）**：
1. `docker compose up mysql`（3307 映射本地测试端口）
2. 插入 `e2e_tester` 账号：`password_hash = PasswordUtils.hash("E2E12345", "e2e_tester")`（`{sha256}`(pwd:phone)）
3. backend（8081）：`JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8` + `SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/jzqs` + `APP_JWT_SECRET=xxx` + `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false` + `WECHAT_DEV_MODE=true` → `mvn spring-boot:run`
4. admin：`npm run dev`（5173，vite proxy `/api`→8081）
5. `cd backend/scripts/e2e/browser && npm i && npx playwright install chromium && npm test`

> 踩坑：Windows 上 `mvn spring-boot:run` 默认 JVM `file.encoding=GBK` 会让 Spring Boot 拒绝启动（`mandatoryFileEncoding=UTF-8`），必须 `JAVA_TOOL_OPTIONS` 显式指定。

## 说明

- 脚本改造已完成，但**浏览器 E2E 未在本地实际跑通**——需要同时起 admin（vite 5173）与 backend
  （8080），当前本地仅 `jzqs-mysql` 容器在跑。跑法：
  `npm i -D @playwright/test mysql2 && npx playwright install chromium && npx playwright test admin-e2e.spec.js`
- 核心业务链路（下单 / 扣款 / 派单 / 一致性）已由 **03 号工单的 `CrossEndFlowE2ETest`** 覆盖并跑通，
  该测试随 `mvn test` 自动进入 CI，不需要浏览器。
