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
# 详细文档见 docs/deployment.md
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
  # 测试由 GitHub Actions test job 在每次 push 时承担（铁律 3：CI 必须跑测试）。
  # 部署构建跳过测试（-Dmaven.test.skip=true，连编译都不做），
  # 避免依赖服务器测试环境（服务器无测试库/存在未跟踪残留测试，曾导致部署时 112 个测试 Error，2026-08-29）。
  # 手动验证测试：./build.sh test 或 mvn test。
  info "用 Maven 容器打包后端 jar（跳过测试，测试由 CI 承担）..."
  docker run --rm \
    --network host \
    -v "$PWD":/app \
    -v "$HOME/.m2":/root/.m2 \
    -w /app/backend \
    "$MAVEN_IMAGE" \
    mvn -B -s /app/backend/.mvn/settings.xml clean package -Dmaven.test.skip=true
  [ -f "$JAR_PATH" ] || fail "打包失败：未生成 $JAR_PATH"
  ok "后端 jar 已生成：$JAR_PATH"
}

run_backend_tests() {
  info "运行后端测试（不打包）..."
  docker run --rm \
    --network host \
    -v "$PWD":/app \
    -v "$HOME/.m2":/root/.m2 \
    -w /app/backend \
    "$MAVEN_IMAGE" \
    mvn -B -s /app/backend/.mvn/settings.xml test
  ok "后端测试通过"
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
      prune_old_images
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
  prune_old_images
  ok "前端已部署，请浏览器硬刷新（Ctrl+Shift+R / Cmd+Shift+R）清除缓存"
}

# 清理构建残留的旧 jzqs 镜像与缓存（只删无人引用的，不影响运行中容器和数据库 volume）
prune_old_images() {
  info "清理构建残留的旧镜像与缓存 ..."
  docker image prune -f >/dev/null 2>&1 || true
  docker images --format '{{.Repository}}:{{.Tag}}' | grep '^jzqs-' | while read -r img; do
    if ! docker ps --format '{{.Image}}' | grep -qx "$img"; then
      docker rmi "$img" >/dev/null 2>&1 && info "已删除旧镜像: $img" || true
    fi
  done
  # 只清 3 天前的构建缓存：保留近期 cache 加速 docker compose build（后端多阶段构建复用依赖层），
  # 之前 -f 全清导致每次部署全量下载依赖（服务器网络慢，部署曾 20 分钟）
  docker builder prune -f --filter "until=72h" >/dev/null 2>&1 || true
  ok "旧镜像/构建缓存清理完成"
}

show_status() {
  docker compose ps
}

# ---------- 参数分发 ----------
ACTION="${1:-all}"
case "$ACTION" in
  backend) deploy_backend ;;
  admin)   deploy_admin ;;
  test)    run_backend_tests ;;
  all)     deploy_backend && deploy_admin ;;
  status)  show_status ;;
  *)       warn "未知参数: $ACTION（支持 backend / admin / test / all / status）"; exit 1 ;;
esac

ok "完成。若页面无变化，请硬刷新浏览器；仍有问题见 docs/deployment.md。"
