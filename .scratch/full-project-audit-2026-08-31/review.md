# 全项目专业审查报告（2026-08-31）

> 审查方式：smart S8 审查流程。3 个并行子 agent（后端/前端/文档）+ 主上下文 git/CI/测试实跑验证。
> 所有结论均有文件路径或命令输出证据。

## 〇、总评

**不是屎山，是一栋结构良好但欠维护的楼**：分层清晰、CI 规范、幂等落库、钱包原子扣减等硬核基础都在，四端测试全绿。
但有 3 个 P0（admin 双写僵尸测试、巨型组件、云函数双写）和一批 P1（后端上帝类、事务边界过宽、模块测试盲区）。
**结论：不需要推倒重来，做 4 个方向的局部重构即可。**

## 一、测试运行证据（本轮实跑）

| 端 | 命令 | 结果 |
|---|---|---|
| backend | `mvn test` | **83 套件 / 283 测试 / 0 失败 0 跳过**（exit 0） |
| admin | `npm run test` (vitest) | **33 文件 / 148 测试全绿**（70s） |
| miniapp | `node --test` | **33 测试全绿** |
| miniapp-rider | `node --test` | **30 测试全绿** |
| cloudfunctions | 未实跑 | cleanStorage 14 个 / cleanupReceipts 12 个测试存在（审查合格） |

CI（`.github/workflows/deploy.yml`）部署前强制跑全量后端测试 + Playwright E2E，时区 Asia/Shanghai 处理正确，符合铁律 3。

## 二、P0 问题（立即处理）

### P0-1 admin 双写僵尸测试链（最阴险的问题）
- `admin/temp-test/` 10 个文件被 git 追踪，是从巨型组件抽出的 **JS 纯函数副本**
- `admin/scripts/*.test.mjs`（12 个文件）import 这些副本做测试
- 但 `vitest.config.ts` 的 include 是 `src/**/*.test.{ts,tsx}` 且 **exclude `scripts/**`** → 这 12 个测试**不被任何自动流程执行**（CI 也不跑 admin 单测）
- 组件实际用的是 `src/modules/**/xxxPage.helpers.ts`（TS 正主），与 temp-test 的 JS 副本**双写**，测试保护不了真实代码（违反铁律 5/7）
- 证据：`admin/src/modules/dashboard/dashboardPage.helpers.ts:13`（正主）vs `admin/temp-test/modules/dashboard/dashboardPage.helpers.js:1`（副本）
- **修复**：测试 import 改指向 src 的 TS 正主（vitest 支持 TS），合并两版差异后删除 temp-test 与 scripts/*.test.mjs（或把有价值的测试迁入 src/）

### P0-2 admin 巨型组件（屎山重灾区）
| 文件 | 行数 |
|---|---:|
| `admin/src/modules/settings/SystemSettingsSectionPage.tsx` | 1898 |
| `admin/src/modules/orders/OrderPrepPage.tsx` | 1667 |
| `admin/src/modules/customers/CustomerAssetPage.tsx` | 1601 |
| `admin/src/modules/settings/SystemSettingsPage.tsx` | 1058 |
| `admin/src/modules/dispatch/DispatchAreasPage.tsx` | 932 |
| 其余 500+ 行组件 | 共 7 个 |

- **修复**：helpers 已抽出（见 P0-1），顺势把 UI 区块拆子组件 + 复用 Radix/Tailwind 基础上已有的 `src/shared/components`（Modal/Tag/AppSelect 已存在且有测试）

### P0-3 cloudfunctions 回执清理双写（违反铁律 5）
- `cloudfunctions/cleanStorage/index.js`（205 行）：直接查 `receipt_files` 删云文件
- `cloudfunctions/cleanupReceipts/index.js`（271 行）：走后端 `/api/internal/receipts/*` 桥接再删
- 两个云函数做同一件事，职责重叠
- **修复**：二选一为权威入口（建议保留 cleanupReceipts，它有后端状态同步），废弃 cleanStorage

## 三、P1 问题（需要改进）

### 后端（Spring Boot 3.3.5 / Java 17 / MyBatis-Plus）
1. **上帝类**：`SettingsServiceImpl` 1355 行、`CustomerAssetServiceImpl` 1164（建档+地址+钱包+套餐+订阅+删除全在一个类）、`DispatchAssignmentModule` 1138、`MobileAuthServiceImpl` 1134、`RiderQueueSupport` 1101、`OrderQueryRepository` 690
2. **事务边界过宽**：`MobilePortalServiceImpl` 30 处 `@Transactional`，全项目仅 3 处 `readOnly=true` → 查询占写事务，浪费连接池
3. **内存限流**：`common/aop/store/InMemoryRateLimitStore.java` 多实例部署失效（当前单实例可接受，上量前必须换 Redis）
4. **目录规范分裂**：order/admin 用 `persistence/`，其余用 `mapper/`；dispatch 直接 import `admin/persistence/AdminRowMappers`（跨模块越界）
5. **事务内外呼**：`OrderDispatchServiceImpl.java:40-48` 在事务内调 `dispatchService.autoAssignPendingOrders()`

### 测试盲区（对照 84 个测试文件 vs 459 个主文件）
- **零测试模块**：`rider`、`audit`、`internal`
- **薄弱模块**：`wallet` 仅 1 个 `MealWalletTest`（纯对象测试），钱包并发扣减/加餐/事务回滚路径靠 `CustomerAssetServiceImplTest` 间接覆盖
- 无属性测试（钱包守恒、终态订单不挂活跃派单这类不变量适合 property-based）
- 已有的测试质量不错：`DispatchAreaDeleteSoftTest` 有 DB 行级断言 + 异常路径

### 前端其他
6. `miniapp/utils/api-base.js` 与 `miniapp-rider/utils/api-base.js` **完全相同**（复制维护）
7. `miniapp-rider/pages/order-detail/index.js` 863 行（订单详情+导航+电话+送达+异常上报+图片上传全塞一起）
8. `miniapp/pages/order/index.js` 769 行
9. API base URL `https://jzqs.top` 硬编码在 api-base.js（建议配置层区分环境）

## 四、做得好的（保持，别动）

- 幂等落库：`IdempotentAspect` + `DbIdempotencyStore` + `V21__idempotency_records` 迁移（符合铁律）
- 钱包原子扣减：`CustomerAssetServiceImpl.java:523-535` 条件 UPDATE（`(total_meals - consumed_meals) >= ?`），非读改写
- CI 强制测试 + 时区对齐 + JWT secret 处理注释清晰
- admin 技术栈现代：React 18 + TS strict + SWR + Radix + Tailwind，`shared/api/http.ts` 统一 axios 封装
- 小程序请求统一走 `utils/request`，无一页绕过
- `.gitignore` 覆盖良好（uploads/class/node_modules/.env 都有规则），`.env` 未被追踪
- `.scratch/` 工单全部新鲜（最后 commit 2026-08-29），无僵尸工单
- application.yml 全环境变量占位，无硬编码密钥

## 五、文档浓缩与文件夹归类方案

| 文件 | 动作 | 理由 |
|---|---|---|
| `SMART.md` (28KB) | **压缩为根目录 pointer（≤50 行）+ 完整版移 `docs/smart-workflow.md`** | 根目录 AI 入口文件太重，重复加载浪费上下文 |
| `DEPLOYMENT.md` | **移 `docs/deployment.md`** | README 保留一句链接即可 |
| `Project_Folders_Structure_Blueprint.md` | **移 `docs/structure-blueprint.md`** | 与 README 目录章节部分重复 |
| `AGENTS.md` | 保留根目录，**删掉与 CONTEXT.md 重复的术语段** | 双写违反单一数据源（术语以 CONTEXT.md 为准） |
| `README.md` / `CONTEXT.md` | 保留根目录不动 | 入口 + 术语唯一真源 |
| `ConnTest.class` | **删除**（未追踪，无 .java 源） | 编译产物 |
| `admin-dev.log` / `backend-run.log` | **删除**（未追踪） | 日志，logs/ 目录才是家 |
| 根目录 `node_modules/` | **删除**（未追踪，根目录无 package.json） | 孤儿目录 |

`.scratch/` 暂不归档（无僵尸工单）。docs/ 目前只有 adr+agents，移动后补齐。

## 六、find-skills 技能调研结论（按语言）

| 方向 | 生态热门 | 本地已有 | 结论 |
|---|---|---|---|
| Java/Spring Boot | `github/awesome-copilot@java-springboot` 19.7K installs | ✅ java-springboot | 本地已覆盖，无需装 |
| React 重构 | 外部最高仅 716 installs（均冷门） | ✅ vercel-react-best-practices + vercel-composition-patterns | 本地 vercel 系更强，无需装 |
| 小程序 | 官方 `wechat-miniprogram/skyline-skills` 系列（845-867）/ gourdbaby 1.2K | ❌ 无 | 可选装官方 skyline 系列（略低于 1K 门槛，观察） |
| SQL/测试 | — | ✅ sql-code-review / mysql / tdd / breakdown-test | 已覆盖 |

## 七、优化路线图（优先级排序）

**第一批（1 次会话，零风险清理 + 假测试转正）**
1. 删根目录垃圾 4 项（上表）
2. P0-1：scripts 测试 import 切到 src 正主 → 删 temp-test
3. 文档移动 3 项 + AGENTS.md 去重

**第二批（admin 重构主线）**
4. 拆 4 个 1000+ 行巨型页面（helpers 已就位，顺势接线）
5. 云函数二合一（废弃 cleanStorage）

**第三批（后端局部重构）**
6. 拆 SettingsServiceImpl / CustomerAssetServiceImpl（钱包逻辑独立成 WalletService）
7. 查询方法补 `readOnly=true`；统一 persistence→mapper 目录
8. 补 rider/audit 测试 + wallet 并发扣减专项测试（属性测试）

**第四批（上量前）**
9. 限流改 Redis；双小程序共享 utils 抽公共包（或至少同步策略文档化）
