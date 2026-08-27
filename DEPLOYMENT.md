# jzqs 部署指南（必读）

> **核心原则**：线上代码永远跟随 `git main` 分支。改完代码 → `git push` → 部署。**手动部署**用 `./build.sh`，**自动部署**由 GitHub Actions 在 push 后自动完成。任何一步漏了都可能导致线上旧代码。

## 一、整体架构

```
用户浏览器 ──► Caddy(80/443) ──► admin 前端容器 (nginx 静态资源)
                          └──► backend 后端容器 (Spring Boot :8080)
                                      └──► mysql 容器
```

- 容器编排：根目录 `docker-compose.yml`
- 反向代理：`Caddyfile`
- 环境变量：根目录 `.env`（不入库，服务器上保留一份）

## 二、部署方式（二选一）

### 方式一：自动部署（推荐，已配置 GitHub Actions）

```bash
git add -A && git commit -m "..." && git push origin main
```

推送后 GitHub Actions 会自动 SSH 到服务器：
`git pull` → `./build.sh backend` → `./build.sh admin`，约 5~10 分钟完成（后端多阶段构建含 mvn 编译）。

> 首次需在 GitHub 仓库 Settings → Secrets and variables → Actions 配置：
> - `SERVER_HOST`：服务器公网 IP
> - `SERVER_USER`：`root`
> - `SERVER_SSH_KEY`：服务器 `/root/.ssh/deploy_key` 的内容（生成命令见下）
> - `SERVER_PORT`（可选，默认 22）

生成 deploy key（服务器上执行一次）：

```bash
ssh-keygen -t ed25519 -f /root/.ssh/deploy_key -N "" -C "github-actions-deploy"
cat /root/.ssh/deploy_key.pub >> ~/.ssh/authorized_keys
cat /root/.ssh/deploy_key   # 复制内容到 GitHub Secrets 的 SERVER_SSH_KEY
```

### 方式二：手动部署

```bash
cd /root/jzqs
./build.sh all        # 全量：后端 + 前端
./build.sh backend    # 只部署后端
./build.sh admin      # 只部署前端
./build.sh status     # 查看容器状态
```

部署完成后浏览器**硬刷新**（`Ctrl+Shift+R` / `Cmd+Shift+R`）清除缓存。

## 三、分模块说明

### 1. 后端 backend（Spring Boot）

- `backend/Dockerfile` 为**多阶段构建**：Maven 在镜像内编译最新源码 → JRE 运行。
- 每次 `docker compose build backend` 都基于最新源码重新打包，**不存在"旧 jar"问题**。
- **不要再手动挂载 jar、不要再单独 mvn 打包**（旧的 bind-mount 方案已废弃）。
- 每次后端重启会**自动执行 Flyway 迁移**（`backend/src/main/resources/db/migration/V*.sql`），新增迁移脚本无需手动跑 SQL。

### 2. 前端 admin（管理后台）

- `admin/Dockerfile` 内部含 `npm run build`，`docker compose build admin` 即重新打包。
- 部署后浏览器必须硬刷新，否则可能命中浏览器缓存（与服务器无关）。

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
curl -s http://localhost/api/health  # 后端存活
```

## 五、常见问题排查

| 症状 | 原因 | 解决 |
|------|------|------|
| 改完前端代码，刷新页面没变化 | 浏览器缓存 | 硬刷新 `Ctrl+Shift+R` |
| 后端接口返回旧逻辑 | 后端容器没重新构建/重启 | `./build.sh backend`（多阶段构建会重新编译） |
| 新迁移脚本没生效 | 后端未重启 | 重启 backend，Flyway 自动执行 |
| 容器起不来/健康检查失败 | 端口占用、编译失败、SQL 错误 | `docker compose logs backend` 查看日志 |
| 域名无法访问 | Caddy 未重启 / 证书问题 | `docker compose restart caddy` |
| GitHub Actions 部署失败 | Secrets 未配 / key 无效 | 检查 Settings→Secrets，重新 `cat /root/.ssh/deploy_key` 配置 |

## 六、回滚

```bash
# 后端：切到旧 commit 重新构建即可
git checkout <旧commit> && ./build.sh backend
git checkout main   # 回滚后切回主分支

# 数据库：Flyway 不支持自动降级，需要手动写补偿 SQL
```

## 七、生产注意事项

- `.env` 不入库，服务器上需保留一份。
- `backend/uploads`（volume `backend_uploads`）存用户上传图片，**不要删除 volume**，否则图片丢失。
- 所有容器 `restart: unless-stopped`，服务器重启后会自动拉起。
- **安全**：MySQL 的 `3306` 端口当前映射到宿主机，若不需要公网直连数据库，建议改成只监听本机（`127.0.0.1:3306:3306`），外部访问走 SSH 隧道（见下节）。

## 八、本地访问服务器数据库 / 日志（企业常见做法）

### 数据库：SSH 隧道（不用把 3306 暴露公网）

```bash
# 本地终端执行：把服务器的 MySQL 映射到本机 3306
ssh -N -L 3306:127.0.0.1:3306 root@你的服务器IP
# 然后本地 Navicat / DataGrip / mysql 客户端连 127.0.0.1:3306 即可
```

### 日志：三种选择（从简到繁）

1. **最简**：`docker logs -f jzqs-backend`（在服务器上实时看）
2. **推荐**：VS Code 安装「Remote - SSH」扩展，本地 IDE 直接打开服务器 `/root/jzqs`，在集成终端里看日志、改代码、跑命令，体验与本地开发一致。
3. **进阶**：部署 Dozzle（轻量 Docker 日志 Web UI）或 Loki + Grafana 集中日志，浏览器看全部容器日志。

企业通常：数据库禁止暴露公网（SSH 隧道/跳板机访问），日志走集中采集（ELK/Loki/云厂商日志服务），并在本地 IDE 远程开发。
