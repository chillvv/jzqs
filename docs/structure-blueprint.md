# Project Folders Structure Blueprint — jzqs

> 本文档是 jzqs 多模块工程的目录结构规范蓝图，用于保持跨技术栈一致的代码组织。
> 由 `folder-structure-blueprint-generator` skill 生成，最后更新：2026-08-22。

---

## 1. 工程总览

jzqs 是一个**多模块单体仓库（monorepo）**，包含以下相互独立的部署单元：

| 模块 | 技术栈 | 部署形态 | 包管理器 |
|------|--------|----------|----------|
| `backend/` | Java 17 + Spring Boot 3.3 (Maven) | 独立服务 / Docker | Maven (`pom.xml`) |
| `admin/` | React 18 + TypeScript + Vite + shadcn/ui | Web 管理后台 / Docker + Nginx | npm (`package.json`) |
| `miniapp/` | 微信小程序（原生） | 微信小程序 | 微信开发者工具 |
| `miniapp-rider/` | 微信小程序（原生，骑手端） | 微信小程序 | 微信开发者工具 |
| `cloudfunctions/` | 微信云函数（Node.js） | 微信云开发 | npm（每函数独立） |

根目录承载跨模块的基础设施：`docker-compose.yml`、`Caddyfile`(反向代理)、`build.sh`(构建编排)。

**组织原则**：按"部署单元 / 技术栈"横向切分顶层目录，每个模块内部再按"分层 / 功能"组织。

---

## 2. 目录可视化（深度 2）

```
jzqs/
├── backend/                  # Java Spring Boot 后端
│   ├── src/main/             # 主源码（java / resources / sql）
│   │   ├── java/             # 按包分层：controller / service / repo / entity...
│   │   └── resources/        # application.yml, mapper, static 等
│   ├── src/test/             # 单元测试（已被 .gitignore 忽略）
│   ├── target/               # Maven 构建输出（忽略）
│   ├── pom.xml               # 依赖与构建定义
│   ├── Dockerfile
│   └── uploads/              # 运行时上传目录（忽略）
├── admin/                    # React 管理前端
│   ├── src/
│   │   ├── app/              # 路由布局（AdminLayout 等）
│   │   ├── modules/          # 业务模块（auth / customers ...）
│   │   ├── shared/           # 跨模块共享组件/工具
│   │   ├── lib/              # 第三方封装/工具库
│   │   ├── styles/           # 全局样式
│   │   ├── App.tsx / main.tsx
│   │   └── setupTests.ts
│   ├── public/               # 静态资源
│   ├── dist/                 # Vite 构建输出（忽略）
│   ├── node_modules/         # 依赖（忽略）
│   ├── vite.config.ts / tailwind.config.js / components.json
│   ├── Dockerfile / nginx.conf
│   └── package.json
├── miniapp/                  # 微信小程序（用户端）
│   ├── pages/ components/ utils/ assets/ images/ custom-tab-bar/
│   ├── tests/                # 测试（忽略）
│   ├── app.js / app.json / app.wxss / project.config.json
│   └── project.private.config.json  # 本地配置（忽略）
├── miniapp-rider/            # 微信小程序（骑手端）
│   ├── pages/ components/ utils/ services/ assets/ custom-tab-bar/ cloudfunctions/
│   ├── tests/                # 测试（忽略）
│   └── app.js / app.json / app.wxss / project.config.json
├── cloudfunctions/           # 微信云函数（每函数独立部署）
│   └── cleanupReceipts/      # config.json + index.js + package.json + node_modules
├── .gitignore
├── docker-compose.yml        # 多服务编排
├── Caddyfile                 # 反向代理
└── build.sh                  # 构建脚本
```

---

## 3. 关键目录分析

### 3.1 Java 后端（`backend/`）
- **分层组织**：`controller / service / repository / entity / dto / config` 经典 Spring 分层，按包（package）映射目录。
- **资源文件**：`src/main/resources/` 下放置 `application.yml`、MyBatis mapper、SQL 脚本；本地配置 `application-local.yml` 已被忽略。
- **测试**：`src/test/` 与 `src/main/` 镜像结构，单独忽略不进生产。
- **构建产物**：`target/`（含 `.class`/`.jar`）一律忽略。

### 3.2 React 管理前端（`admin/`）
- **模块优先**：`src/modules/<feature>/` 内聚页面 + 逻辑 + 组件；`shared/` 放跨模块复用件。
- **样式**：Tailwind（`tailwind.config.js`）+ `src/index.css` 全局样式；`styles/` 放主题/变量。
- **UI 库**：shadcn/ui（`components.json`），组件源位于 `src/` 内。
- **构建输出**：`dist/`、`node_modules/`、各类 `.log` 忽略。

### 3.3 微信小程序（`miniapp/`、`miniapp-rider/`）
- **原生四件套**：每个页面/组件由 `.js / .json / .wxml / .wxss` 组成，同目录就近放置。
- **共享**：`utils/`（请求/工具）、`components/`（通用组件）、`assets/`、`images/`。
- **端区分**：`miniapp` = 用户端，`miniapp-rider` = 骑手端，结构对称。
- **本地配置**：`project.private.config.json` 忽略。

### 3.4 云函数（`cloudfunctions/`）
- 每个子目录是一个**独立云函数**，自带 `package.json` + `node_modules`（独立部署）。
- `config.json` 描述云函数配置，`index.js` 为入口。
- `node_modules/` 通过根级 `node_modules/` 规则忽略。

---

## 4. 文件放置规则

| 文件类型 | 放置位置 |
|----------|----------|
| 后端接口 | `backend/src/main/java/.../controller/` |
| 业务逻辑 | `backend/src/main/java/.../service/` |
| 数据实体/DTO | `backend/src/main/java/.../entity/` 或 `/dto/` |
| 前端业务页 | `admin/src/modules/<feature>/` |
| 前端共享组件 | `admin/src/shared/` |
| 小程序页面 | `miniapp(pages|components)/<name>/`（四件套同目录） |
| 配置文件 | 各模块根 `*.config.*` / `application*.yml` |
| 文档 | `docs/`（已忽略，本地规格不入仓） |

---

## 5. 命名约定

- **Java**：包/类 `PascalCase`，方法/变量 `camelCase`。
- **前端**：组件 `PascalCase.tsx`，工具/hook `camelCase.ts`，目录 `kebab-case` 或 `camelCase`。
- **小程序**：页面/组件目录 `kebab-case`，文件与目录同名。
- **云函数**：目录 `camelCase`。

---

## 6. 构建与输出

- 后端：`mvn package` → `backend/target/`（忽略）。
- 前端：`npm run build` → `admin/dist/`（忽略）。
- 小程序：微信开发者工具上传，无本地构建产物目录。
- 编排：`build.sh` 串联各模块构建；`docker-compose.yml` 统一编排容器。

---

## 7. Git 忽略策略（已落地于 `.gitignore`）

**必须忽略、绝不上传**：

1. **依赖**：`node_modules/`（各模块/云函数）。
2. **构建产物**：`backend/target/`、`admin/dist/`、通用 `dist/build/out/.output`、`.class/.jar/.map/.tsbuildinfo`。
3. **日志/临时**：`*.log/*.out/*.err`、`backend/*.txt`、`*.tmp`、`backend/tmp_*`。
4. **本地配置**：`.env*`、`application-local.yml`、`project.private.config.json`、`.miniprogram/`。
5. **IDE/OS**：`.idea/ .vscode/ *.iml .DS_Store Thumbs.db`。
6. **AI/工具产物**：`.trae/ .claude/ .kiro/ .codebuddy/` 等本地 agent 状态。
7. **质量/分析产物**：`.quality-admin.json`、`DEAD_CODE_REPORT.md`、`err.txt`、Vitest 临时 `vitest.config.ts.timestamp-*`、`*.timestamp-*.mjs`。
8. **测试资源**：各模块 `tests/`、`*.test.ts(x)`、`admin/scripts/`、`admin/temp-test/`。

> 注：已被 git 跟踪的历史文件（如 `DEAD_CODE_REPORT.md`）如需退出版本库，需 `git rm` 后提交删除。

---

## 8. 结构维护

- 新增业务模块：后端加包、前端加 `modules/<feature>/`、小程序加 `pages/<name>/`。
- 跨模块复用：优先放入 `admin/src/shared/` 或后端公共包，避免重复。
- 结构变更：更新本文档并在 PR 描述中说明。
- 提交前自检：`git status` 不应出现 `node_modules/`、`target/`、`dist/`、`.env` 等。
