#!/usr/bin/env bash
# E2E 链路 H：排菜单 → 订阅预览 → 管理员确认 → 自动生成订单 + 扣餐
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_08"
PHONE="13900001111"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"
# 清理明天菜单排期（menu_week_items 表，避免重复排期报错）
mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "DELETE FROM menu_week_items WHERE serve_date='$SERVE_DATE';" 2>/dev/null

echo "===== 链路H：订阅确认生成订单（serveDate=$SERVE_DATE） ====="

echo "[1/6] 管理员排明天午餐菜单"
MENU=$(curl -s -X POST $BASE/api/admin/menu-schedules -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"mealName\":\"E2E测试套餐\",\"mealDetail\":\"测试主菜+配菜\",\"calories\":600,\"merchantNote\":\"E2E菜单\"}")
echo "     排期响应(前200字): ${MENU:0:200}"
[ "$(echo "$MENU" | jget "['code']")" = "OK" ] && ok "午餐菜单已排期" || bad "菜单排期失败: ${MENU:0:150}"

echo "[2/6] 顾客注册 + 加地址 + 保存订阅规则（午餐）"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":10,"validityDays":30,"remark":"E2E链路H"}' > /dev/null
ADDR_H=$(curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$PHONE\",\"addressLine\":\"E2E订阅地址H\",\"areaCode\":\"中南路区域\",\"isDefault\":true}")
ADDR_H_ID=$(echo "$ADDR_H" | jget "['data']['id']")
RULE=$(curl -s -X POST $BASE/api/mobile/customer/subscription-rule -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"weekDays":"1,2,3,4,5","lunchEnabled":true,"dinnerEnabled":false}')
[ "$(echo "$RULE" | jget "['code']")" = "OK" ] && [ -n "$ADDR_H_ID" ] && ok "订阅规则+地址已保存（addressId=$ADDR_H_ID）" || bad "规则/地址保存失败: $RULE $ADDR_H"

echo "[3/6] 订阅预览（确认订阅项列表）"
PREVIEW=$(curl -s "$BASE/api/admin/orders/subscription-preview?serveDate=$SERVE_DATE" -H "Authorization: Bearer $ADMIN_TOKEN")
PREV_CNT=$(echo "$PREVIEW" | jget "['data']" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null)
[ "$(echo "$PREVIEW" | jget "['code']")" = "OK" ] && ok "订阅预览接口正常（$PREV_CNT 条）" || bad "订阅预览异常: ${PREVIEW:0:150}"

echo "[4/6] 管理员批量导入订阅（bulk-import）→ 生成订单"
IMPORT=$(curl -s -X POST $BASE/api/admin/orders/bulk-import-subscription -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"items\":[{\"customerId\":$CUST_ID,\"mealPeriod\":\"LUNCH\",\"deliveryMealPeriod\":\"LUNCH\",\"addressId\":$ADDR_H_ID,\"note\":\"E2E链路H订阅\"}]}")
echo "     导入响应(前300字): ${IMPORT:0:300}"
[ "$(echo "$IMPORT" | jget "['code']")" = "OK" ] && ok "订阅导入成功（订单已生成）" || bad "导入失败: ${IMPORT:0:200}"

echo "[5/6] 验证订单已生成 + 钱包扣餐"
ORDER_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM meal_slot_orders mso JOIN daily_orders do ON do.id=mso.daily_order_id WHERE do.customer_id=$CUST_ID;")
CONSUMED=$(MYSQL jzqs -e "SELECT COALESCE(consumed_meals,0) FROM meal_wallets WHERE customer_id=$CUST_ID AND active=1;")
echo "     订单数=$ORDER_CNT consumed=$CONSUMED"
[ "$ORDER_CNT" = "1" ] && ok "订阅导入已生成 1 个订单" || bad "订单未生成: 订单数=$ORDER_CNT"
[ "$CONSUMED" = "1" ] && ok "钱包已扣餐 consumed=1（下单即扣）" || bad "钱包扣餐异常: consumed=$CONSUMED"

echo "[6/6] 清理排期菜单（保持测试库干净）"
mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "DELETE FROM menu_week_items WHERE serve_date='$SERVE_DATE';" 2>/dev/null
ok "已清理测试菜单排期"

echo ""
echo "===== 链路H结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
