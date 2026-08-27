#!/usr/bin/env bash
# E2E 链路 D：改地址 → 派单区域重算 → 空壳绑定写入 → 商家重新分配
# 重点验证：reconcileDispatchArea 的 INSERT...SELECT 列数（9列9值）修复 + 快照置 PENDING
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_04"
PHONE="13900005555"
AREA_A="街道口区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路D：改址区域重算（serveDate=$SERVE_DATE） ====="

echo "[1/6] 顾客注册 + 授权 + 保存两个地址（A 有区域 / B 无区域）"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":5,"validityDays":30,"remark":"E2E链路D"}' > /dev/null
ADDR_A=$(curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$PHONE\",\"addressLine\":\"E2E老地址A\",\"areaCode\":\"$AREA_A\",\"isDefault\":true}")
ADDR_A_ID=$(echo "$ADDR_A" | jget "['data']['id']")
ADDR_B=$(curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$PHONE\",\"addressLine\":\"E2E新地址B(无区域)\",\"areaCode\":\"\",\"isDefault\":false}")
ADDR_B_ID=$(echo "$ADDR_B" | jget "['data']['id']")
[ -n "$ADDR_A_ID" ] && [ -n "$ADDR_B_ID" ] && ok "地址A=$ADDR_A_ID 地址B=$ADDR_B_ID" || bad "地址保存失败 A=$ADDR_A B=$ADDR_B"

echo "[2/6] 下单（地址A）+ 派单到 $AREA_A"
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E老地址A\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
curl -s -X POST $BASE/api/admin/dispatch/pending-items/batch-assign -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"orderIds\":[$ORDER_ID],\"areaCode\":\"$AREA_A\",\"updatedBy\":\"E2E\"}" > /dev/null
SNAP_BEFORE=$(MYSQL jzqs -e "SELECT CONCAT('区域=',area_code,' 骑手=',rider_name) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
echo "     派单后快照: $SNAP_BEFORE"
echo "$SNAP_BEFORE" | grep -q "区域=$AREA_A" && ok "派单快照=街道口区域" || bad "派单快照异常"

echo "[3/6] 顾客改地址到 B（无记忆地址）"
CHANGE=$(curl -s -X POST $BASE/api/mobile/customer/orders/$ORDER_ID/change-address -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"addressId\":$ADDR_B_ID}")
echo "     改址响应(前220字): ${CHANGE:0:220}"
[ "$(echo "$CHANGE" | jget "['code']")" = "OK" ] && ok "改址成功" || bad "改址失败（若为列数错位 bug 则说明未修复）"

echo "[4/6] 验证空壳绑定写入（area_code 空 / rider NULL / ADDRESS_CHANGED_PENDING_CONFIRM）"
SHELL=$(MYSQL jzqs -e "
  SELECT CONCAT('area_code=[', COALESCE(area_code,'NULL'), '] rider=[', COALESCE(CAST(rider_profile_id AS CHAR),'NULL'), '] reason=', updated_reason)
  FROM rider_address_bindings WHERE customer_id=$CUST_ID AND address_id=$ADDR_B_ID;")
echo "     空壳绑定: $SHELL"
echo "$SHELL" | grep -q "reason=ADDRESS_CHANGED_PENDING_CONFIRM" && ok "空壳绑定已写入（待确认标记）" || bad "空壳绑定缺失: $SHELL"
echo "$SHELL" | grep -q "area_code=\[\]" && ok "area_code 为空壳值(空串)" || bad "area_code 非空壳: $SHELL"
echo "$SHELL" | grep -q "rider=\[NULL\]" && ok "rider_profile_id 为 NULL" || bad "rider 残留: $SHELL"

echo "[5/6] 验证订单已切地址 + 派单快照置 PENDING（不沿用旧区域）"
SQL=$(MYSQL jzqs -e "
  SELECT CONCAT('订单地址=', mso.address_id, ' 快照区域=', COALESCE(da.area_code,'-'))
  FROM meal_slot_orders mso LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id=mso.id WHERE mso.id=$ORDER_ID;")
echo "     $SQL"
echo "$SQL" | grep -q "订单地址=$ADDR_B_ID" && ok "订单地址已切换为 B" || bad "订单地址未切换"
echo "$SQL" | grep -q "快照区域=PENDING" && ok "快照区域=PENDING（待商家确认）" || bad "快照未置 PENDING: $SQL"

echo "[6/6] 管理员从异常单/待确认视角可找回该订单（moveOrderToArea 重新分配）"
EXC=$(curl -s "$BASE/api/admin/dispatch/exceptions?mealPeriod=LUNCH" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "     异常单接口返回: $(echo "$EXC" | jget "['code']")"
MOVE=$(curl -s -X POST "$BASE/api/admin/dispatch/areas/南湖区域/orders/$ORDER_ID/assign-rider" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"riderName":"于海"}')
echo "     重新分配响应(前220字): ${MOVE:0:220}"
SNAP_AFTER=$(MYSQL jzqs -e "SELECT CONCAT('区域=',area_code,' 骑手=',COALESCE(rider_name,'-')) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
echo "     重分配后快照: $SNAP_AFTER"
echo "$SNAP_AFTER" | grep -q "区域=南湖区域" && ok "订单已重新分配到南湖区域" || bad "重新分配未生效: $SNAP_AFTER"

echo ""
echo "===== 链路D结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
