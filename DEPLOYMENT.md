# jzqs 部署指南（必读）

> **给所有 AI 助手/开发者的提醒**：修改任何代码后，**必须按本文档重新构建对应容器**，否则线上服务器运行的是旧版本。最常见的"我改了代码但页面没变"就是这个原因。

## 一、整体架构

```
用户浏览器 ──► Caddy(80/443) ──► admin 前端容器 (nginx 静态资源)
                          └──► backend 后端容器 (Spring Boot :8080)
                                      └──► mysql 容器
```

- 容器编排：根目录 `docker-compose.yml`
- 反向代理：`Caddyfile`
- 环境变量：根目录 `.env`

## 二、快速部署（推荐）

```bash
cd /root/jzqs
./build.sh all        # 全量：后端 + 前端
./build.sh backend    # 只部署后端
./build.sh admin      # 只部署前端
./build.sh status     # 查看容器状态
```

部署完成后浏览器**硬刷新**（`Ctrl+Shift+R` / `Cmd+Shift+R`）清除缓存。

## 三、分模块部署细节

### 1. 前端 admin（管理后台）

修改了 `admin/src/**` 下的任何文件后：

```bash
docker compose build admin   # 重新打包（内含 npm build）
docker compose up -d admin   # 重启容器
```

**坑（本次已踩）**：
- `admin/` 的 Dockerfile 内部执行 `npm run build`，`dist/` 是构建产物（gitignore 忽略），**修改源码后不 build，容器里永远是旧包**。
- 部署后浏览器必须硬刷新，否则可能命中 nginx 缓存的旧 JS。

### 2. 后端 backend（Spring Boot）

修改了 `backend/src/**` 下任何文件后：

```bash
# 第一步：Maven 容器内打包 jar（本机无需安装 Maven）
docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app \
  maven:3.9.9-eclipse-temurin-17 mvn -B clean package -DskipTests

# 第二步：重新构建并重启
docker compose build backend
docker compose up -d backend
```

**坑**：
- 后端 Dockerfile 只是 `COPY target/backend-0.0.1-SNAPSHOT.jar`，**不编译源码**。必须先 mvn 打包生成 jar，再 build 容器。
- 每次后端重启会**自动执行 Flyway 迁移**（`backend/src/main/resources/db/migration/V*.sql`），新增迁移脚本（如 `V18__xxx.sql`）无需手动跑 SQL。

### 3. 数据库迁移

| 位置 | 说明 |
|------|------|
| `backend/src/main/resources/db/migration/V*.sql` | Flyway 自动执行，后端重启即生效 |

手动验证迁移是否已执行：

```bash
docker exec jzqs-mysql mysql -ujzqs -p jzqs -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

### 4. 微信小程序（miniapp / miniapp-rider）

- **前端代码**：用微信开发者工具打开 `miniapp/`、`miniapp-rider/`，点「上传」，再在小程序后台发布。
- **云函数**：`cloudfunctions/` 下的函数用微信开发者工具右键「上传并部署：云端安装依赖」。
- 小程序依赖后端 `backend`，需先保证后端已部署最新版。

### 5. Caddy 反向代理

只有修改了 `Caddyfile` 才需要重启：

```bash
docker compose restart caddy
```

## 四、部署后验证

```bash
docker compose ps                    # 所有容器应为 Up + healthy
curl -s http://localhost/api/health  # 后端存活（或 /actuator/health）
```

## 五、常见问题排查

| 症状 | 原因 | 解决 |
|------|------|------|
| 改完前端代码，刷新页面没变化 | 未重新 build admin 容器，或浏览器缓存 | `./build.sh admin` + 硬刷新 |
| 后端接口返回旧逻辑 | 未重新打包 jar / 未重启 backend | `./build.sh backend` |
| 新迁移脚本没生效 | 后端未重启 | 重启 backend，Flyway 自动执行 |
| 容器起不来/健康检查失败 | 端口占用、jar 缺失、SQL 错误 | `docker compose logs backend` 查看日志 |
| 域名无法访问 | Caddy 未重启 / 证书问题 | `docker compose restart caddy` |

## 六、回滚

```bash
# 后端：切到旧代码重新打包即可
cd /root/jzqs && ./build.sh backend

# 数据库：Flyway 不支持自动降级，需要手动写补偿 SQL
```

## 七、生产注意事项

- `.env` 不入库，服务器上需保留一份。
- `backend/uploads`（volume `backend_uploads`）存用户上传图片，**不要删除 volume**，否则图片丢失。
- 所有容器 `restart: unless-stopped`，服务器重启后会自动拉起。
