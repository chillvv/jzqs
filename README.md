# jzqs

多模块工程（monorepo），包含微信小程序服务端全栈：Java 后端、React 管理后台、两个微信小程序、微信云函数。

## 模块一览

| 模块 | 技术栈 | 说明 | 本地运行 |
|------|--------|------|----------|
| `backend/` | Java 17 + Spring Boot 3.3 (Maven) | 主后端服务 | `mvn spring-boot:run` |
| `admin/` | React 18 + TypeScript + Vite + shadcn/ui | Web 管理后台 | `npm install && npm run dev` |
| `miniapp/` | 微信小程序（原生） | 用户端 | 微信开发者工具打开 |
| `miniapp-rider/` | 微信小程序（原生） | 骑手端 | 微信开发者工具打开 |
| `cloudfunctions/` | 微信云函数 (Node.js) | `cleanupReceipts`（定时清理回执云图片） | 微信云开发部署 |

## 目录结构

详见 [`docs/structure-blueprint.md`](./docs/structure-blueprint.md)。

```
jzqs/
├── backend/          # Java 后端 (Maven)
├── admin/            # React 管理前端 (Vite)
├── miniapp/          # 微信小程序 - 用户端
├── miniapp-rider/    # 微信小程序 - 骑手端
├── cloudfunctions/   # 微信云函数
├── docker-compose.yml
├── Caddyfile         # 反向代理
├── build.sh          # 构建编排
└── .env.example      # 环境变量示例（复制为 .env 后填写）
```

## 快速开始

```bash
# 1. 环境变量
cp .env.example .env        # 按需修改 .env（不入库）

# 2. 后端
cd backend && mvn spring-boot:run

# 3. 管理前端
cd admin && npm install && npm run dev

# 4. 小程序：用微信开发者工具分别打开 miniapp/ 与 miniapp-rider/
```

## 部署（重要，AI 必读）

- **修改任何代码后必须重新构建对应容器，否则线上运行的是旧版本。**
- 完整部署流程与坑位见 **[`docs/deployment.md`](./docs/deployment.md)**，一键部署用 **`./build.sh`**：

  ```bash
  ./build.sh all        # 全量部署（后端 + 前端）
  ./build.sh backend    # 只部署后端（含 Flyway 自动迁移）
  ./build.sh admin      # 只部署管理后台前端
  ./build.sh status     # 查看容器状态
  ```

- 各模块均有独立 `Dockerfile`；`docker-compose.yml` 统一编排。
- `Caddyfile` 提供反向代理；`build.sh` 串联多模块构建。

## 环境变量

- 后端：Spring Boot 配置在 `backend/src/main/resources/application*.yml`；本地配置 `application-local.yml` 不入库。
- 根 `.env` 供 `docker-compose.yml` 使用，复制自 `.env.example`。

## 文档与规范

- 目录结构规范：[`docs/structure-blueprint.md`](./docs/structure-blueprint.md)
- 忽略规则：见 `.gitignore`（依赖、构建产物、日志、本地配置、AI 工具产物等一律不上传）
