#!/usr/bin/env bash
# E2E 链路 I：骑手注册 → 接单（配送队列）→ 完成配送（回执 + DELIVERED）
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
RIDER_PHONE="13900006660"
RIDER_NAME="端到端骑手"
RIDER_OPENID="e2e_rider_01"
CUST_OPENID="e2e_openid_09"
CUST_PHONE="13900000009"
AREA="光谷软件园区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CCID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$CUST_OPENID' LIMIT 1;")
[ -n "$CCID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CCID;"
RCID=$(MYSQL jzqs -e "SELECT id FROM rider_profiles WHERE openid='$RIDER_OPENID' LIMIT 1;")
[ -n "$RCID" ] && MYSQL jzqs -e "DELETE FROM rider_profiles WHERE id=$RCID;"
# 清理并重建明天菜单（下单会校验餐次有可售菜品）
mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "DELETE FROM menu_week_items WHERE serve_date='$SERVE_DATE';" 2>/dev/null

echo "===== 链路I：骑手端配送（serveDate=$SERVE_DATE） ====="

echo "[0/7] 排明天午餐菜单（下单前置条件）"
MENU=$(curl -s -X POST $BASE/api/admin/menu-schedules -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"mealName\":\"E2E骑手测试套餐\",\"mealDetail\":\"主菜+配菜\",\"calories\":550,\"merchantNote\":\"E2E\"}")
[ "$(echo "$MENU" | jget "['code']")" = "OK" ] && ok "明天午餐菜单已排期" || bad "菜单排期失败: ${MENU:0:120}"

echo "[1/7] 骑手注册（SQL 预置 PENDING 骑手 → register-phone 绑定拿 token）"
# 模拟 wx-login 已建立的 PENDING 骑手；register-phone 校验手机号已存在骑手记录
mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e \
  "INSERT INTO rider_profiles (rider_name, phone, display_name, current_openid, auth_status, employment_status, created_at, updated_at)
   VALUES ('$RIDER_NAME', '$RIDER_PHONE', '$RIDER_NAME', '$RIDER_OPENID', 'PENDING', 'ACTIVE', NOW(), NOW());" 2>/dev/null
REG=$(curl -s -X POST $BASE/api/auth/register-phone -H "Content-Type: application/json" \
  -d "{\"phone\":\"$RIDER_PHONE\",\"nickname\":\"$RIDER_NAME\",\"openid\":\"$RIDER_OPENID\",\"userType\":\"RIDER\"}")
echo "     注册响应(前250字): ${REG:0:250}"
RIDER_TOKEN=$(echo "$REG" | jget "['data']['token']")
RIDER_ID=$(echo "$REG" | jget "['data']['userId']")
RID=$(MYSQL jzqs -e "SELECT id FROM rider_profiles WHERE phone='$RIDER_PHONE';")
[ -n "$RID" ] && [ -n "$RIDER_TOKEN" ] && ok "骑手注册成功 riderId=$RID" || bad "骑手注册失败: ${REG:0:150}"

echo "[2/7] 顾客注册 + 授权 + 下单"
CREG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$CUST_OPENID\",\"phone\":\"$CUST_PHONE\"}")
CUST_TOKEN=$(echo "$CREG" | jget "['data']['token']")
CUST_ID=$(echo "$CREG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":5,"validityDays":30,"remark":"E2E链路I"}' > /dev/null
curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$CUST_PHONE\",\"addressLine\":\"E2E骑手配送地址\",\"areaCode\":\"$AREA\",\"isDefault\":true}" > /dev/null
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E骑手配送地址\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
[ -n "$ORDER_ID" ] && ok "顾客下单成功 orderId=$ORDER_ID" || bad "下单失败: ${ORDER:0:150}"

echo "[3/7] 管理员指派该骑手配送订单"
ASSIGN=$(curl -s -X POST "$BASE/api/admin/dispatch/areas/$AREA/orders/$ORDER_ID/assign-rider" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"riderName\":\"$RIDER_NAME\"}")
echo "     指派响应(前220字): ${ASSIGN:0:220}"
DISP=$(MYSQL jzqs -e "SELECT CONCAT('派单=',status,' 骑手=',rider_name) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
echo "     $DISP"
echo "$DISP" | grep -q "骑手=$RIDER_NAME" && ok "订单已指派给测试骑手" || bad "指派未生效: $DISP"

echo "[4/7] 骑手查看配送队列"
QUEUE=$(curl -s "$BASE/api/rider/orders?serveDate=$SERVE_DATE" -H "Authorization: Bearer $RIDER_TOKEN")
echo "     队列响应(前300字): ${QUEUE:0:300}"
BATCH_ID=$(echo "$QUEUE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
items = d.get('data', {}).get('items', [])
for it in items:
    if str(it.get('mealSlotOrderId')) == '$ORDER_ID':
        print(it.get('batchItemId')); break
" 2>/dev/null)
echo "     batchItemId=$BATCH_ID"
[ -n "$BATCH_ID" ] && ok "骑手队列含该订单（批次项 id=$BATCH_ID）" || bad "骑手队列未找到订单"

echo "[5/7] 骑手完成配送（提交回执，参数为 mealSlotOrderId）"
COMPLETE=$(curl -s -X POST $BASE/api/rider/orders/$ORDER_ID/complete -H "Authorization: Bearer $RIDER_TOKEN" -H "Content-Type: application/json" \
  -d '{"receiptNote":"E2E骑手已送达","deliveredAt":""}')
echo "     完成响应(前250字): ${COMPLETE:0:250}"
[ "$(echo "$COMPLETE" | jget "['code']")" = "OK" ] && ok "骑手完成配送成功" || bad "完成失败: ${COMPLETE:0:200}"

echo "[6/7] 验证订单 DELIVERED + 回执生成"
SQL=$(MYSQL jzqs -e "SELECT CONCAT('订单=',mso.status,' 回执数=',(SELECT COUNT(*) FROM delivery_receipts dr WHERE dr.meal_slot_order_id=mso.id)) FROM meal_slot_orders mso WHERE mso.id=$ORDER_ID;")
echo "     $SQL"
echo "$SQL" | grep -q "订单=DELIVERED" && ok "订单已 DELIVERED" || bad "订单未送达: $SQL"
echo "$SQL" | grep -q "回执数=1" && ok "骑手回执已生成" || bad "回执缺失: $SQL"

echo "[7/7] 骑手端 summary + 订单完成状态"
SUM=$(curl -s "$BASE/api/rider/summary" -H "Authorization: Bearer $RIDER_TOKEN")
echo "     summary: $(echo "$SUM" | jget "['code']")"
[ "$(echo "$SUM" | jget "['code']")" = "OK" ] && ok "骑手端 summary 正常" || bad "summary 异常: ${SUM:0:150}"

echo ""
echo "===== 链路I结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
