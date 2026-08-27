#!/usr/bin/env bash
# ============================================================
# 初始化后端集成测试库 jzqs_test
#
# 作用：DROP + 重建 jzqs_test，按 Flyway 版本号序执行全部迁移脚本，
#      并在 V25（外键约束）前清理 V1 种子 dump 中自带的悬空引用，
#      保证测试库 schema 与生产一致且能通过外键校验。
#
# 用法：
#   backend/scripts/init-test-db.sh            # 使用默认连接信息
#   MYSQL_PASSWORD=xxx backend/scripts/init-test-db.sh
#
# 依赖：docker（jzqs-mysql 容器）、host 有 mysql 客户端
# ============================================================
set -euo pipefail

MIGRATION_DIR="$(cd "$(dirname "$0")/../src/main/resources/db/migration" && pwd)"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-jzqs}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-jzqs_password_123}"
ROOT_PASSWORD="${ROOT_PASSWORD:-root_password_123}"
TEST_DB="${TEST_DB:-jzqs_test}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-jzqs-mysql}"

say()  { printf "\033[1;34m[INFO]\033[0m %s\n" "$*"; }
fail() { printf "\033[1;31m[FAIL]\033[0m %s\n" "$*" >&2; exit 1; }

mysql_cmd() {
  command mysql --default-character-set=utf8mb4 -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$@"
}

# 1) 用 root 重建测试库
say "重建测试库 ${TEST_DB} ..."
docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$ROOT_PASSWORD" -e "
  DROP DATABASE IF EXISTS ${TEST_DB};
  CREATE DATABASE ${TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  GRANT ALL PRIVILEGES ON ${TEST_DB}.* TO '${MYSQL_USER}'@'%';
  FLUSH PRIVILEGES;
" || fail "重建测试库失败，请确认 ${MYSQL_CONTAINER} 容器在运行"

# 2) 按版本号序执行迁移（跳过 V25/V26：V25 需先清理孤儿数据，V26 统一在最后执行）
say "执行迁移脚本（V1-V24，跳过 V25/V26）..."
for f in $(ls "$MIGRATION_DIR"/V*.sql | sort -V); do
  base="$(basename "$f")"
  case "$base" in
    V25*|V26*) say "跳过 ${base}（待孤儿清理后统一执行）" ;;
    *)         mysql_cmd "$TEST_DB" < "$f" || fail "迁移失败: ${base}" ;;
  esac
done

# 3) 清理 V1 dump 中自带的悬空引用（与「上线前一致性巡检」口径一致）
say "清理 V1 dump 中的悬空引用（孤儿派单/批次项/回执/售后）..."
mysql_cmd "$TEST_DB" -e "
  DELETE da FROM dispatch_assignments da
    LEFT JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
    WHERE mso.id IS NULL;
  DELETE dbi FROM dispatch_batch_items dbi
    LEFT JOIN meal_slot_orders mso ON mso.id = dbi.meal_slot_order_id
    WHERE mso.id IS NULL;
  DELETE dr FROM delivery_receipts dr
    LEFT JOIN meal_slot_orders mso ON mso.id = dr.meal_slot_order_id
    WHERE mso.id IS NULL;
  DELETE ac FROM aftersale_cases ac
    LEFT JOIN meal_slot_orders mso ON mso.id = ac.meal_slot_order_id
    WHERE mso.id IS NULL;
" || fail "孤儿清理失败"

# 4) 执行 V25 / V26（外键 + 批次唯一键）
say "执行 V25 / V26 ..."
mysql_cmd "$TEST_DB" < "$MIGRATION_DIR"/V25__order_child_cascade_foreign_keys.sql || fail "V25 失败"
mysql_cmd "$TEST_DB" < "$MIGRATION_DIR"/V26__dedup_dispatch_batches_unique_scope.sql || fail "V26 失败"

# 5) 验证关键约束存在
say "验证关键约束 ..."
mysql_cmd "$TEST_DB" -e "
  SELECT COUNT(*) AS meal_wallets_unique FROM information_schema.statistics
    WHERE table_schema='${TEST_DB}' AND table_name='meal_wallets' AND index_name='uk_meal_wallets_active_customer';
  SELECT COUNT(*) AS subscription_rules_unique FROM information_schema.statistics
    WHERE table_schema='${TEST_DB}' AND table_name='subscription_rules' AND index_name='uk_subscription_rules_customer';
  SELECT COUNT(*) AS idempotency_unique FROM information_schema.statistics
    WHERE table_schema='${TEST_DB}' AND table_name='idempotency_records' AND index_name='uk_idempotency_records_key_hash';
  SELECT COUNT(*) AS binding_3key FROM information_schema.statistics
    WHERE table_schema='${TEST_DB}' AND table_name='rider_address_bindings' AND index_name='uk_rider_address_bindings_customer_address';
  SELECT COUNT(*) AS batch_scope_unique FROM information_schema.statistics
    WHERE table_schema='${TEST_DB}' AND table_name='dispatch_batches' AND index_name='uk_dispatch_batches_scope';
" || true

say "测试库 ${TEST_DB} 初始化完成"
