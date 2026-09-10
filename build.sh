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
#   - 后端由 backend/Dockerfile 多阶段构建：镜像内用 Maven 从 src 重新编译打包，
#     宿主机无需安装 Maven，也不依赖 backend/target 下的产物（该目录已被 .dockerignore 排除）。
#     构建后必须重启容器才生效。
#   - 前端 admin 的 Dockerfile 内部含 npm build 阶段，
#     修改源码后必须重新 build admin，否则容器里仍是旧包！
#   - 数据库结构变更（db/migration/V*.sql）由 Flyway 在
#     后端启动时自动执行，无需手动操作。
#   - 部署前会把当前后端镜像打上 jzqs-backend:rollback 标签（只保留上一版）作为回滚点，
#     新版本出问题可一键回滚：
#       docker tag jzqs-backend:rollback jzqs-backend:local && docker compose up -d backend
#
# 详细文档见 docs/deployment.md
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 变量 ----------
MAVEN_IMAGE="maven:3.9.9-eclipse-temurin-17"
BACKEND_IMAGE="jzqs-backend:local"
ROLLBACK_TAG="jzqs-backend:rollback"

# ---------- 工具函数 ----------
info()  { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
ok()    { printf "\033[1;32m[ OK ]\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m[WARN]\033[0m %s\n" "$*"; }
fail()  { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*" >&2; exit 1; }

# 部署前把当前镜像存为回滚点（只保留上一版，不占额外空间）
save_backend_rollback_point() {
  if ! docker image inspect "$BACKEND_IMAGE" >/dev/null 2>&1; then
    return 0
  fi
  docker tag "$BACKEND_IMAGE" "$ROLLBACK_TAG"
  ok "已保存回滚点：$ROLLBACK_TAG（$(docker images -q "$BACKEND_IMAGE")）"
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
  # 先留回滚点再构建：镜像内会从 src 重新编译，不需要宿主先打包一遍
  save_backend_rollback_point
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
  # 容器可能用「不带 tag 的名字」引用镜像（如 compose 里写 image: jzqs-admin），
  # 所以按镜像 ID 判断是否在用：否则 jzqs-admin:latest 会被误判为无人使用、每次部署都尝试删一次。
  local in_use_ids=""
  local used_ref used_id
  while read -r used_ref; do
    [ -z "$used_ref" ] && continue
    used_id=$(docker image inspect "$used_ref" --format '{{.Id}}' 2>/dev/null || true)
    [ -n "$used_id" ] && in_use_ids="$in_use_ids $used_id"
  done < <(docker ps -a --format '{{.Image}}' | sort -u)

  local img img_id
  while read -r img img_id; do
    # 回滚点必须保留，否则新版本出问题就只能重新构建旧代码
    if [ "$img" = "$ROLLBACK_TAG" ]; then
      info "保留回滚镜像: $img"
      continue
    fi
    # 在用镜像保留
    if [ -n "$in_use_ids" ] && [[ " $in_use_ids " == *" $img_id "* ]]; then
      continue
    fi
    docker rmi "$img" >/dev/null 2>&1 && info "已删除旧镜像: $img" || true
  done < <(docker images --no-trunc --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep '^jzqs-' || true)
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
