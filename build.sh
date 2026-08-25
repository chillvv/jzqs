#!/usr/bin/env bash
# ============================================================
# jzqs 一键构建 & 部署脚本
#
# 用法：
#   ./build.sh backend   # 只构建并重启后端（含 Flyway 迁移）
#   ./build.sh admin     # 只构建并重启管理后台前端
#   ./build.sh all       # 全量构建（默认）
#   ./build.sh status    # 查看当前容器状态
#
# 说明：
#   - 后端 jar 由 Maven 在容器内打包（本机无需安装 Maven），
#     产物为 backend/target/backend-0.0.1-SNAPSHOT.jar，
#     Dockerfile 直接 COPY 该 jar，构建后必须重启容器才生效。
#   - 前端 admin 的 Dockerfile 内部含 npm build 阶段，
#     修改源码后必须重新 build admin，否则容器里仍是旧包！
#   - 数据库结构变更（db/migration/V*.sql）由 Flyway 在
#     后端启动时自动执行，无需手动操作。
#
# 详细文档见 DEPLOYMENT.md
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 变量 ----------
MAVEN_IMAGE="maven:3.9.9-eclipse-temurin-17"
JAR_PATH="backend/target/backend-0.0.1-SNAPSHOT.jar"

# ---------- 工具函数 ----------
info()  { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
ok()    { printf "\033[1;32m[ OK ]\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m[WARN]\033[0m %s\n" "$*"; }
fail()  { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*" >&2; exit 1; }

build_backend_jar() {
  info "用 Maven 容器打包后端 jar ..."
  docker run --rm \
    -v "$PWD":/app \
    -v "$HOME/.m2":/root/.m2 \
    -w /app/backend \
    "$MAVEN_IMAGE" \
    mvn -B clean package -DskipTests
  [ -f "$JAR_PATH" ] || fail "打包失败：未生成 $JAR_PATH"
  ok "后端 jar 已生成：$JAR_PATH"
}

deploy_backend() {
  build_backend_jar
  info "构建并重启 backend 容器 ..."
  docker compose build backend
  docker compose up -d backend
  info "等待后端健康检查通过 ..."
  for i in $(seq 1 30); do
    status=$(docker inspect -f '{{.State.Health.Status}}' jzqs-backend 2>/dev/null || echo "starting")
    if [ "$status" = "healthy" ]; then
      ok "后端已健康启动（Flyway 迁移已自动执行）"
      return 0
    fi
    sleep 2
  done
  warn "后端 60 秒内未达到 healthy，请检查日志：docker compose logs backend"
}

deploy_admin() {
  info "构建并重启 admin 前端容器 ..."
  docker compose build admin
  docker compose up -d admin
  ok "前端已部署，请浏览器硬刷新（Ctrl+Shift+R / Cmd+Shift+R）清除缓存"
}

show_status() {
  docker compose ps
}

# ---------- 参数分发 ----------
ACTION="${1:-all}"
case "$ACTION" in
  backend) deploy_backend ;;
  admin)   deploy_admin ;;
  all)     deploy_backend && deploy_admin ;;
  status)  show_status ;;
  *)       warn "未知参数: $ACTION（支持 backend / admin / all / status）"; exit 1 ;;
esac

ok "完成。若页面无变化，请硬刷新浏览器；仍有问题见 DEPLOYMENT.md。"
