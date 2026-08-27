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
#   - backend 的 Dockerfile 为多阶段构建（Maven 编译 → JRE 运行），
#     `docker compose build backend` 会自动基于最新源码重新编译打包，
#     不再需要手动 mvn 打包，也不再挂载 jar（避免"旧代码"问题）。
#   - 前端 admin 的 Dockerfile 内部含 npm build 阶段，
#     修改源码后重新 build admin 即生效。
#   - 数据库结构变更（db/migration/V*.sql）由 Flyway 在
#     后端启动时自动执行，无需手动操作。
#   - 可选：RUN_BACKEND_TESTS=1 时先跑 JUnit 测试，失败即终止。
#
# 详细文档见 DEPLOYMENT.md
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

MAVEN_IMAGE="maven:3.9.9-eclipse-temurin-17"

info()  { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
ok()    { printf "\033[1;32m[ OK ]\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m[WARN]\033[0m %s\n" "$*"; }
fail()  { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*" >&2; exit 1; }

run_backend_tests() {
  info "RUN_BACKEND_TESTS=1：执行后端全部测试（JUnit + 真实 MySQL 集成测试）..."
  # 集成测试连宿主 MySQL 的 jzqs_test 库（由 backend/scripts/init-test-db.sh 初始化），
  # maven 容器通过 host.docker.internal 访问宿主，可用 TEST_DB_URL 覆盖。
  docker run --rm \
    -v "$PWD":/app \
    -v "$HOME/.m2":/root/.m2 \
    --add-host=host.docker.internal:host-gateway \
    -e TEST_DB_URL="${TEST_DB_URL:-jdbc:mysql://host.docker.internal:3306/jzqs_test?useUnicode=true\&characterEncoding=utf8\&serverTimezone=Asia/Shanghai}" \
    -e TEST_DB_USER="${TEST_DB_USER:-jzqs}" \
    -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-jzqs_password_123}" \
    -w /app/backend \
    "$MAVEN_IMAGE" \
    mvn -B test || fail "后端测试失败，构建终止（请先运行 backend/scripts/init-test-db.sh 初始化测试库）"
  ok "后端测试全部通过，继续构建 ..."
}

deploy_backend() {
  if [ "${RUN_BACKEND_TESTS:-0}" = "1" ]; then
    run_backend_tests
  else
    warn "测试未执行（RUN_BACKEND_TESTS=1 可开启；建议在 CI 中提供测试库并开启）"
  fi
  info "构建并重启 backend 容器（多阶段构建：自动编译最新源码）..."
  docker compose build backend
  docker compose up -d backend
  info "等待后端健康检查通过 ..."
  for i in $(seq 1 60); do
    status=$(docker inspect -f '{{.State.Health.Status}}' jzqs-backend 2>/dev/null || echo "starting")
    if [ "$status" = "healthy" ]; then
      ok "后端已健康启动（Flyway 迁移已自动执行）"
      return 0
    fi
    sleep 2
  done
  warn "后端 120 秒内未达到 healthy，请检查日志：docker compose logs backend"
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
